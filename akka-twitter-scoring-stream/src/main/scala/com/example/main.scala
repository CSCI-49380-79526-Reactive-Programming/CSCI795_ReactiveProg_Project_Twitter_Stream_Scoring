// Standard dependencies
import akka.actor.{Actor, ActorRef, Props, ActorSystem}
import com.github.tototoshi.csv._
import java.io.File
import java.time._

// ScalaFX GUI
import scalafx.application.{Platform, JFXApp}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.beans.property.{ReadOnlyStringWrapper, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.scene.Scene
import scalafx.scene.control.{TableCell, TableColumn, TableView}

// Twitter4s streaming library
import com.danielasfregola.twitter4s.entities._
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.TwitterStreamingClient


// A unique key for identifying politicians.
class PoliticianKey(val name : String, val party : String, val state : String) {}


// A row for a politician in the GUI.
// NOTE: Is equatable with PoliticianKey (for convience).
class PoliticianRow(key_ : PoliticianKey, score_ : Double) {
  private val key   = key_
  val name  = new ReadOnlyStringWrapper(this, "name" , key_.name )
  val party = new ReadOnlyStringWrapper(this, "party", key_.party)
  val state = new ReadOnlyStringWrapper(this, "state", key_.state)
  val score = new StringProperty(       this, "score", score_.toString)

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
class Politician( val updater    : ActorRef
                , val name       : String
                , val party      : String
                , val state      : String
                , val twitter    : String
                , val term_start : Instant
                , val term_end   : Instant
                ) extends Actor {

  private val key = new PoliticianKey(name, party, state)

  // The following lines exist only to test the functionality of the Critic actor.
  // It has the nice side effect of populating the GUI until we get real Twitter integration.
  val testTweet = new Tweet(created_at=Instant.now(),id=5,id_str="5", source="", text="Hello World")
  updater ! (key,testTweet)

  // TODO:
  //   - Connect to Twitter
  //   - Set up real time stream
  //   - Get Twitter profile information
  //   - Calculate first Instant we can collect data from
  //   - HTTP query for historical data

  def receive = {
    case path : String => 
  }

}


// Actor who maintains the scoring of all politicians and updates the GUI.
// Listens for updates from the Politician actors for new Tweets to be processed.
class Critic(rows_ : ObservableBuffer[PoliticianRow]) extends Actor {

  private val scoring = new scala.collection.mutable.HashMap[PoliticianKey, TwitterScoring]
  private var rows    = rows_

  def receive = {
    case (politician: PoliticianKey, tweet: Tweet) =>
       scoring.get(politician) match {
         case Some(twitterScore) =>
           // We already have a score for this politician
           // We update the state
           twitterScore.update(tweet) match {
             case Some(v) =>
               // The score was updated,
               // We need to update the GUI
               val i = rows.indexOf(politician)
               rows(i).score.value = v.toString
             case None => () // Nothing to update
           }
         
         case None =>
           // Politician is new
           // New to add them to the scoring and the GUI
           scoring.addOne(politician, new TwitterScoring(tweet))
           val score = scoring(politician).pinocchioScore
           rows.append(new PoliticianRow(politician, score))
       }
  }
}


// A Twitter score object.
// Intended to be associated with a politician.
// Internally keeps a mutable state of Tweets received as either disputed or undisputed.
// Can provide a "Pinochiocco Score" based on the current internal state.
class TwitterScoring(tweet_ : Tweet) {
  // Two sets of tweet IDs
  private val disputed   = new scala.collection.mutable.HashSet[Long]
  private val undisputed = new scala.collection.mutable.HashSet[Long]

  if (flagged(tweet_)) {
    disputed.add(tweet_.id)
  }
  else {
    undisputed.add(tweet_.id)
  }

  def flagged(tweet : Tweet) : Boolean = {
    !tweet.withheld_in_countries.isEmpty
  }

  def update(tweet : Tweet) : Option[Double] = {
    val id      = tweet.id
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
    return None
  }
  
  def pinocchioScore() : Double = {
    disputed.size / (disputed.size + undisputed.size)
  }

}


class TwitterSecrets(filePath_ : String) {
  private val csvData   = CSVReader.open(new File(filePath_)).all()
  val consumerKey       = csvData(1)(0)
  val consumerSecret    = csvData(1)(1)
  val accessToken       = csvData(1)(2)
  val accessTokenSecret = csvData(1)(3)
}


object Main extends JFXApp {
  // Create an empty buffer of rows for politicians
  // We will fill this dynamically layer
  val rows  = ObservableBuffer[PoliticianRow]()

  // Construct the GUI
  stage = constructGUI(rows)

  // Set up the actor system and core actors
  val system = ActorSystem("pinocchioScoring")
  val critic = system.actorOf(Props(new Critic(rows)), name = "critic")

  // Get the Twitter authentication secrets
  val secretsFile = "secrets.csv"
  val secrets = new TwitterSecrets(secretsFile)

  // Get the politician input data
  val senatorsFile = "us-senate.csv" 
  val stream  = CSVReader.open(new File(senatorsFile)).iterator
  stream.next // Drop the header row from the stream iterator
  
  // Add all senators as actors to our system
  stream foreach addPolitician(system)(critic)

 /*
    Import works ok, uncommenting it will error as we need to provide
    the necessary env props (Twitter API keys) for it to load properly.
    Will look into this as part of my next task to integrate with Twitter API. -Ye
  */
  // val restClient = TwitterRestClient()
  // val streamingClient = TwitterStreamingClient()

  def addPolitician(system : ActorSystem)(updater : ActorRef )(dataRow : Seq[String]) : Unit = {
    // Convert Seq to Vector for more efficient random access
    val vec     = dataRow.toVector
    // Extract relevent fields from the row
    val name    = vec(15)
    val id      = vec(16)
    val party   = vec(14)
    val state   = vec( 0)
    val twitter = vec(49)
    val begin   = Instant.parse(vec(28) + "T00:00:00.00Z")
    val finish  = Instant.parse(vec(29) + "T00:00:00.00Z")
    // Add a new Politician actor from the row data
    system.actorOf(Props(new Politician(updater, name, party, state, twitter, begin, finish)), name = id)
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
                text = "Score"
                cellValueFactory = { _.value.score }
                prefWidth = 80
              }
            )
          }
        }
      }
  }

}
