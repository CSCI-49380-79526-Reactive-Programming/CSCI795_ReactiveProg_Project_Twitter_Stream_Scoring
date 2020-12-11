package politician

import java.time.temporal.ChronoUnit
import java.time.Instant

import akka.actor.{Actor, ActorRef, Scheduler}

import scala.concurrent.Await
import scala.concurrent.duration._

import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.TwitterRestClient

import scalafx.beans.property.{ReadOnlyStringWrapper, StringProperty}

import tweet.TwitterSecrets

// Unique Politician key to identify politician within our system/UI/stream etc...
class PoliticianKey(val name : String, val party : String, val state : String, val twitter_handle: String) {}

class PoliticianUpdate(val key : PoliticianKey, val positivity : Option[Double], val pinocchio : Option[Double]) {}

// Politician row data for UI
// A row for a politician in the GUI.
// NOTE: Is equatable with PoliticianKey (for convience).
class PoliticianRow(key_ : PoliticianKey, positivity_ : Double, pinocchio_ : Double) {
  private val key     = key_
  val twitter_handle  = key_.twitter_handle
  val name            = new ReadOnlyStringWrapper(this, "name"          , key_.name )
  val party           = new ReadOnlyStringWrapper(this, "party"         , key_.party)
  val state           = new ReadOnlyStringWrapper(this, "state"         , key_.state)
  val positivity      = new StringProperty(       this, "positivity"    , positivity_.toString)
  val pinocchio       = new StringProperty(       this, "pinocchio"     , pinocchio_.toString)
}

// Politician Actor which listens to the Twitter stream of the associated politician.
class Politician( secrets_            : TwitterSecrets
                , val updater         : ActorRef
                , scheduler           : Scheduler
                , key_                : PoliticianKey
                , val term_start      : Instant
                , val term_end        : Instant
                ) extends Actor {
  private val key    = key_
  val name           = key_.name
  val party          = key_.party
  val state          = key_.state
  val twitter_handle = key_.twitter_handle

  // Initial connection to Twitter to query the politician's Twitter user data
  private val (consumerToken, accessToken) = secrets_.getTokens()
  private val restClient = new TwitterRestClient(consumerToken, accessToken)
  getHistoricalTweets()

  // Wait 45 seconds,
  // the query historical data a second time to
  // catch messages that might have heppened during the start-up delta
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  scheduler.scheduleOnce(Duration(45, SECONDS), self, ())
  
  def receive = {
    case () => 
      getHistoricalTweets()
      restClient.shutdown()
  }

  def getHistoricalTweets() : Unit = {
    // Fetch historical tweets by handle
    println("Fetching Historical Tweets: " + name + " - @" + twitter_handle)
    val historicalTweets = Await.result(restClient.userTimelineForUser(screen_name = twitter_handle, count = 25), Duration.Inf).data

    // The following lines exist only to test the functionality of the Critic actor.
    // It has the nice side effect of populating the GUI until we get real Twitter integration.
    for (t <- historicalTweets) {
      val tweet = new Tweet(created_at=Instant.now(),id=t.id,id_str=t.id_str, source="", text=t.text)
      updater ! (key,tweet)
    }
  }

}