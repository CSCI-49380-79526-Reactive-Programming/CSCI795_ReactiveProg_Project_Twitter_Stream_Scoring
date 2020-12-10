// Standard dependencies
import akka.actor.{Actor, ActorRef, Props, ActorSystem}
import com.github.tototoshi.csv._
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.collection.immutable.Map
import scala.concurrent.Await
import scala.concurrent.duration._

// ScalaFX GUI
import scalafx.application.{Platform, JFXApp}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.beans.property.{ReadOnlyStringWrapper, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.scene.Scene
import scalafx.scene.control.{TableCell, TableColumn, TableView}

// Twitter4s streaming library
import com.danielasfregola.twitter4s.entities._
import com.danielasfregola.twitter4s.http.clients.rest.users._
import com.danielasfregola.twitter4s.http.clients.streaming.TwitterStream
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken, Tweet}
import com.danielasfregola.twitter4s.entities.streaming.StreamingMessage

// A unique key for identifying politicians.
class PoliticianKey(val name : String, val party : String, val state : String) {}


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
  restClient.shutdown()

  // Unique numeric ID for the politician's Twitter account
  private val userID     = userData.id

  // The earliest time we can query tweets for this politician
  private val userEpoch  = List(userData.created_at, term_start, Instant.now().minus(7, ChronoUnit.DAYS)).max

  // Set up a stream of live tweets from the politician
  private val streamingClient = TwitterStreamingClient(consumerToken, accessToken)
  streamingClient.filterStatuses(follow = Seq(userID), stall_warnings = true)(forwardTweetText)

  // The following lines exist only to test the functionality of the Critic actor.
  // It has the nice side effect of populating the GUI until we get real Twitter integration.
  val testTweet = new Tweet(created_at=Instant.now(),id=5,id_str="5", source="", text="We love scala! All aboard the scala train!")
  updater ! (key,testTweet)

  // TODO:
  //   - Connect to Twitter
  //   - Set up real time stream - Done on the filterStatuses(...) call above
  //   - Get Twitter profile information - Done on the rest call above
  //   - Calculate first Instant we can collect data from
  //   - HTTP query for historical data

  def forwardTweetText: PartialFunction[StreamingMessage, Unit] = {
    case tweet: Tweet =>
      println("Tweet Received: [@" + twitter + "] - " + tweet.text)
     updater ! (key,tweet)
  }

  def receive = {
    case path : String => 
  }

}


class Collator (updater_ : ActorRef) extends Actor {
  private val collation = new scala.collection.mutable.HashMap[Long, PoliticianKey]

  def receive = {
    case () => sender ! collation.keySet

    case (id: Long, politician : PoliticianKey) =>
      collation.addOne(id, politician)
      
    case (id: Long, tweet: Tweet) =>
      collation.get(id) match {
        case Some(politician) => updater_ ! (politician, tweet)
        case None => 
      }
  }
}


// Actor who maintains the scoring of all politicians and updates the GUI.
// Listens for updates from the Politician actors for new Tweets to be processed.
class Critic(wordRanking_ : Map[String,Int], rows_ : ObservableBuffer[PoliticianRow]) extends Actor {

  private val scoring   = new scala.collection.mutable.HashMap[PoliticianKey, TwitterScoring]
  private var rows      = rows_
  private var wordRanks = wordRanking_

  def receive = {
    case (politician: PoliticianKey, tweet: Tweet) =>
       scoring.get(politician) match {
         case Some(twitterScore) =>
           // We already have a score for this politician
           // We update the state
           twitterScore.update(tweet) match {
             case (Some(x), Some(y)) =>
               // Both scores were updated,
               // We need to update the GUI
               val i = rows.indexOf(politician)
               rows(i).positivity.value = x.toString
               rows(i).pinocchio.value  = y.toString
             case (Some(x), None) =>
               // A score was updated,
               // We need to update the GUI
               val i = rows.indexOf(politician)
               rows(i).positivity.value = x.toString
             case (None, Some(y)) =>
               // A score was updated,
               // We need to update the GUI
               val i = rows.indexOf(politician)
               rows(i).pinocchio.value  = y.toString
             case (None, None) => () // Nothing to update
           }
         
         case None =>
           // Politician is new
           // New to add them to the scoring and the GUI
           scoring.addOne(politician, new TwitterScoring(wordRanks, tweet))
           val positivity = scoring(politician).positivityScore
           val pinocchio  = scoring(politician).pinocchioScore
           rows.append(new PoliticianRow(politician, positivity, pinocchio))
       }
  }
}


// A Twitter score object.
// Intended to be associated with a politician.
// Internally keeps a mutable state of Tweets received as either disputed or undisputed.
// Can provide a "Pinochiocco Score" based on the current internal state.
class TwitterScoring(wordRanking_ : Map[String,Int], tweet_ : Tweet) {
  // Two sets of tweet IDs for Pinocchio score
  private val disputed   = new scala.collection.mutable.HashSet[Long]
  private val undisputed = new scala.collection.mutable.HashSet[Long]

  // Two sets of tweet IDs for Positivity score
  private val wordRanks  = wordRanking_
  private val positive   = new scala.collection.mutable.HashSet[Long]
  private val negative   = new scala.collection.mutable.HashSet[Long]
  private val neutral    = new scala.collection.mutable.HashSet[Long]

  updatePositivityScore(tweet_)
  updatePinocchioScore(tweet_)

  def flagged(tweet : Tweet) : Boolean = {
    !tweet.withheld_in_countries.isEmpty
  }

  def update(tweet : Tweet) : (Option[Double], Option[Double]) = {
    val x = updatePositivityScore(tweet)
    val y = updatePinocchioScore(tweet)
    (x, y)
  }

  def updatePinocchioScore(tweet : Tweet) : Option[Double] = {
    val id = tweet.id
    if (disputed contains id) {
      // We already processed this tweet as disputed
      if (!flagged(tweet)) {
        // However it's status has changed to undisputed
        disputed.remove(id)
        undisputed.add(id)
        return Some(pinocchioScore)
       }
       // And no change is required
    }
    else if (undisputed contains id) {
      // We processed this tweet as undisputed,
      if (flagged(tweet)) {
        // However it's status has changed to disputed
        disputed.add(id)
        undisputed.remove(id)
        return Some(pinocchioScore)
      }
      // And no change is required
    }
    else {
      // We have *not* processed this tweet
      if (flagged(tweet)) {
        disputed.add(id)
      }
      else {
        undisputed.add(id)
      }
      return Some(pinocchioScore)
    }
    None
  }

  def updatePositivityScore(tweet : Tweet) : Option[Double] = {
    val id = tweet.id

    if ((positive contains id) || (neutral contains id) || (negative contains id)) {
      // We already processed this tweet
      return None
    }

    // Otherwise analyse and process the tweet
    val score = analyzeText(tweet.text)
    if (score == 0) {
      neutral.add(id)
    }
    else if (score > 0) {
      positive.add(id)
    }
    else {
      negative.add(id)
    }
    Some(positivityScore)
  }

  def pinocchioScore() : Double = {
    disputed.size / (disputed.size + undisputed.size)
  }
  
  def positivityScore() : Double = {
    (positive.size - negative.size) / (positive.size + neutral.size + negative.size)
  }

  def analyzeText(text : String) : Int = {
    // Get rid of punctuation
    // Make every character lower-case
    // Split on whitespace
    val cleanedStream = text.replaceAll("""[\p{Punct}&&[^.]]""", "")
                            .toLowerCase()
                            .split("\\ +")

    cleanedStream.foldLeft(0) { (x : Int, word : String) =>
      wordRanks.get(word) match {
        case Some(v) => x + v
        case None    => x
      }
    }
  }

}


class TwitterSecrets(filePath_ : String) {
  private val csvData = CSVReader.open(new File(filePath_)).all()
  private val consumerKey       = csvData(1)(0)
  private val consumerSecret    = csvData(1)(1)
  private val accessTokenKey    = csvData(1)(2)
  private val accessTokenSecret = csvData(1)(3)

  private val consumerToken = ConsumerToken(
    key     = consumerKey,
    secret  = consumerSecret
  )
  private val accessToken = AccessToken(
    key     = accessTokenKey,
    secret  = accessTokenSecret
  )

  def getTokens() : (ConsumerToken, AccessToken) = {
    return (consumerToken, accessToken)
  }

}


object Main extends JFXApp {
  // Create an empty buffer of rows for politicians
  // We will fill this dynamically layer
  val rows  = ObservableBuffer[PoliticianRow]()

  // Construct the GUI
  stage = constructGUI(rows)

  // Get word rankings
  val wordsFile = "AFINN-111.csv"
  val wordRanks = gatherWordPositivity(wordsFile)

  // Set up the actor system and core actors
  val system = ActorSystem("Politician-Scoring")
  val critic = system.actorOf(Props(new Critic(wordRanks, rows)), name = "critic")

  // Get the Twitter authentication secrets
  val secretsFile = "secrets.csv"
  val secrets = new TwitterSecrets(secretsFile)

  // Get the politician input data
  val senatorsFile = "us-senate-test.csv"
  val stream  = CSVReader.open(new File(senatorsFile)).iterator
  stream.next // Drop the header row from the stream iterator
  
  // Add all senators as actors to our system
  stream foreach addPolitician(system)(critic)

/*
  // val restClient = TwitterRestClient()
  val (consumerToken, accessToken) = secrets.getTokens()
  val streamingClient = TwitterStreamingClient(consumerToken, accessToken)
  def printTweetText: PartialFunction[StreamingMessage, Unit] = {
    case tweet: Tweet => println(tweet.text)
  }

  streamingClient.sampleStatuses(stall_warnings = true)(printTweetText)
*/

  def addPolitician(system : ActorSystem)(updater : ActorRef )(dataRow : Seq[String]) : Unit = {
    // Convert Seq to Vector for more efficient random access
    val vec     = dataRow.toVector
    // Extract relevent fields from the row
    val name    = vec(15)
    val id      = vec(16)
    val party   = vec(14)
    val state   = vec( 0)
    val twitter = vec(48)
    val begin   = Instant.parse(vec(28) + "T00:00:00.00Z")
    val finish  = Instant.parse(vec(29) + "T00:00:00.00Z")
    // Add a new Politician actor from the row data
    system.actorOf(Props(new Politician(secrets, updater, name, party, state, twitter, begin, finish)), name = id)
  }

  def constructGUI(rows : ObservableBuffer[PoliticianRow]) : PrimaryStage = {
    new PrimaryStage {
        title.value = "CSCI-795 — Politician Pinocchio Presentation"
        scene = new Scene {
          content = new TableView[PoliticianRow](rows) {
            columns ++= List(
              new TableColumn[PoliticianRow, String] {
                text = "Name"
                cellValueFactory = { _.value.name }
                prefWidth = 170
              },
              new TableColumn[PoliticianRow, String] {
                text = "Party"
                cellValueFactory = { _.value.party }
                prefWidth = 100
              },
              new TableColumn[PoliticianRow, String] {
                text = "State"
                cellValueFactory = { _.value.state }
                prefWidth = 120
              },
              new TableColumn[PoliticianRow, String] {
                text = "Positivity"
                cellValueFactory = { _.value.positivity }
                prefWidth = 80
              },
              new TableColumn[PoliticianRow, String] {
                text = "Pinocchio"
                cellValueFactory = { _.value.pinocchio }
                prefWidth = 80
              }
            )
          }
        }
      }
  }

  def gatherWordPositivity(filePath : String) : Map[String,Int] = {
    val stream  = CSVReader.open(new File(filePath)).iterator
    val mapping = new scala.collection.immutable.HashMap[String,Int]()
    stream.foldLeft(mapping){ (m, row) =>
      m.updated(row(0), row(1).toInt)
    }

  }

}
