package sentinel
import io.AnsiColor._

import akka.actor.{Actor, ActorRef}
import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration._

// Twitter4s streaming library
import com.danielasfregola.twitter4s.http.clients.rest.users._
import com.danielasfregola.twitter4s.http.clients.streaming.TwitterStream
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken, Tweet}
import com.danielasfregola.twitter4s.entities.streaming.StreamingMessage

import politician.PoliticianKey
import tweet.TwitterSecrets

// Actor who listens for Tweets
class Sentinel(secrets_ : TwitterSecrets, updater_ : ActorRef, politiciansToStream : Iterator[PoliticianKey]) extends Actor {

    // Collect the Twitter IDs required for streaming
    private val (consumerToken, accessToken) = secrets_.getTokens()
    private val restClient         = TwitterRestClient(consumerToken, accessToken)
    private var twitterIDsToStream = politiciansToStream.map(getTwitterID(restClient)).toSeq
    restClient.shutdown()

    // Start streaming for incoming tweets.
    private val streamingClient = TwitterStreamingClient(consumerToken, accessToken)
    streamingClient.filterStatuses(stall_warnings = true, follow = twitterIDsToStream)(printFilteredStatusTweet)

    def getTwitterID(restClient : TwitterRestClient)(p: PoliticianKey) : Long = {
      // println("Connecting to live twitter stream: @" + p.twitter_handle)
      val userData = Await.result(restClient.user(screen_name = p.twitter_handle), Duration.Inf).data
      println(s"${RED}Connecting to live twitter stream: @" + p.twitter_handle + " - ID: " + userData.id)
      userData.id
    }

    // Function to process incoming tweet stream
    def printFilteredStatusTweet: PartialFunction[StreamingMessage, Unit] = {
      case tweet: Tweet => {
        val receiveTweetUserId = tweet.user.get.id
        val receiveTweetUserScreenName = tweet.user.get.screen_name
        println("@" + receiveTweetUserScreenName + " (" + receiveTweetUserId + ") : " + tweet.text)

        // Only send/update if the ID was part of the senator IDs we are tracking.
        // This prevents mentions/retweets from triggering score updates.
        if (twitterIDsToStream.contains(receiveTweetUserId)) {
          println(s"\n${RED}${CYAN_B}[UPDATING SCORE] @" + receiveTweetUserScreenName + " (" + receiveTweetUserId + ") : " + tweet.text + "\n")

          // Send below tweet to critic
          val newTweet = new Tweet(created_at=Instant.now(),id=tweet.id, id_str=tweet.id_str, source="", text=tweet.text)
          
          // We don't really care about the other key fields (used for display) to update.
          val key = new PoliticianKey("_", "_", "_", receiveTweetUserScreenName)
          updater_ ! (key, newTweet)
        }
      }
    }

    def receive = {
      case () => ()
    }
    
}
