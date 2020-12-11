package politician

import java.time.temporal.ChronoUnit
import java.time.Instant

import akka.actor.{Actor, ActorRef}

import scala.concurrent.Await
import scala.concurrent.duration._

import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.TwitterRestClient

import scalafx.beans.property.{ReadOnlyStringWrapper, StringProperty}

import tweet.TwitterSecrets

// Unique Politician key to identify politician within our system/UI/stream etc...
class PoliticianKey(val name : String, val party : String, val state : String, val twitter_handle: String) {}

// Politician row data for UI
// A row for a politician in the GUI.
// NOTE: Is equatable with PoliticianKey (for convience).
class PoliticianRow(key_ : PoliticianKey, positivity_ : Double, pinocchio_ : Double) {
  private val key     = key_
  val name            = new ReadOnlyStringWrapper(this, "name"      , key_.name )
  val twitter_handle  = new ReadOnlyStringWrapper(this, "twitter_handle"    , key_.twitter_handle)
  val party           = new ReadOnlyStringWrapper(this, "party"     , key_.party)
  val state           = new ReadOnlyStringWrapper(this, "state"     , key_.state)
  val positivity      = new StringProperty(       this, "positivity", positivity_.toString)
  val pinocchio       = new StringProperty(       this, "pinocchio" , pinocchio_.toString)

  def canEqual(a: Any) = a.isInstanceOf[PoliticianRow]

  override def equals(that: Any): Boolean =
        that match {
            case that: PoliticianKey => {
                this.key == that
            }
            case that: PoliticianRow => {
                that.canEqual(this) &&
                this.key == that.key
            }
            case _ => false
        }
}

// Politician Actor which listens to the Twitter stream of the associated politician.
class Politician( secrets_            : TwitterSecrets
                , val updater         : ActorRef
                , val name            : String
                , val party           : String
                , val state           : String
                , val twitter_handle  : String
                , val term_start      : Instant
                , val term_end        : Instant
                ) extends Actor {

  private val key = new PoliticianKey(name, party, state, twitter_handle)
  private val (consumerToken, accessToken) = secrets_.getTokens()

  // Initial connection to Twitter to query the politician's Twitter user data
  private val restClient = new TwitterRestClient(consumerToken, accessToken)

  // Fetch historical tweets by handle
  println("Fetching Historical Tweets: " + name + " - @" + twitter_handle)
  private val historicalTweets = Await.result(restClient.userTimelineForUser(screen_name = twitter_handle, count = 25), Duration.Inf).data

  restClient.shutdown()

  // The following lines exist only to test the functionality of the Critic actor.
  // It has the nice side effect of populating the GUI until we get real Twitter integration.
  for (t <- historicalTweets) {
      val tweet = new Tweet(created_at=Instant.now(),id=t.id,id_str=t.id_str, source="", text=t.text)
      updater ! (key,tweet)
  }

  // val testTweet = new Tweet(created_at=Instant.now(),id=5,id_str="5", source="", text="We love scala! All aboard the scala train!")


  def receive = {
    case path : String => 
  }
}