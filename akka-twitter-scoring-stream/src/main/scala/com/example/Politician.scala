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
class PoliticianKey(val name : String, val party : String, val state : String) {}

// Politician row data for UI
// A row for a politician in the GUI.
// NOTE: Is equatable with PoliticianKey (for convience).
class PoliticianRow(key_ : PoliticianKey, positivity_ : Double, pinocchio_ : Double) {
  private val key   = key_
  val name       = new ReadOnlyStringWrapper(this, "name"      , key_.name )
  val party      = new ReadOnlyStringWrapper(this, "party"     , key_.party)
  val state      = new ReadOnlyStringWrapper(this, "state"     , key_.state)
  val positivity = new StringProperty(       this, "positivity", positivity_.toString)
  val pinocchio  = new StringProperty(       this, "pinocchio" , pinocchio_.toString)

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
class Politician( secrets_       : TwitterSecrets
                , val updater    : ActorRef
                , val name       : String
                , val party      : String
                , val state      : String
                , val twitter    : String
                , val term_start : Instant
                , val term_end   : Instant
                ) extends Actor {

  private val key = new PoliticianKey(name, party, state)
  private val (consumerToken, accessToken) = secrets_.getTokens()

  // Initial connection to Twitter to query the politician's Twitter user data
  private val restClient = new TwitterRestClient(consumerToken, accessToken)
  private val userData   = Await.result(restClient.user(screen_name = twitter), Duration.Inf).data

  // Unique numeric ID for the politician's Twitter account
  private val userID = userData.id

  private val historicalTweets = Await.result(restClient.userTimelineForUserId(user_id = userID, count = 25), Duration.Inf).data

  restClient.shutdown()

  // The earliest time we can query tweets for this politician
  private val userEpoch  = List(userData.created_at, term_start, Instant.now().minus(7, ChronoUnit.DAYS)).max

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