// Standard dependencies
import akka.actor.{Actor, ActorRef, Props, ActorSystem}
import com.github.tototoshi.csv._
import java.io.File
import java.time.Instant

// ScalaFX GUI
import scalafx.application.{JFXApp}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.collections.ObservableBuffer
import scalafx.scene.Scene
import scalafx.scene.control.{TableColumn, TableView}

// Twitter4s streaming library
import com.danielasfregola.twitter4s.http.clients.rest.users._
import com.danielasfregola.twitter4s.http.clients.streaming.TwitterStream
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken, Tweet}
import com.danielasfregola.twitter4s.entities.streaming.StreamingMessage

// Our own classes
import scorer.{Collator, Critic}
import politician.{Politician, PoliticianKey, PoliticianRow}
import tweet.{TwitterScoring, TwitterSecrets}

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

  // val (consumerToken, accessToken) = secrets.getTokens()
  // val streamingClient = TwitterStreamingClient(consumerToken, accessToken)

  // def printFilteredStatusTweet: PartialFunction[StreamingMessage, Unit] = {
  //   case tweet: Tweet => {
  //     val receiveTweetUserId = tweet.user.get.id
  //     val receiveTweetUserScreenName = tweet.user.get.screen_name
  //     println("\n@" + receiveTweetUserScreenName + " (" + receiveTweetUserId + ") : " + tweet.text + "\n")
  //     // Broadcast to politician key generator here -> to borad cast to critic actor.
  //     // Send twitter ID, and tweet text.
  //   }
  // }

  // TODO: Add all the rest of the politician ID once we are able to consolidate all the politician ids.
  val MY_TWITTER_ID = 2891210047L

  // streamingClient.filterStatuses(stall_warnings = true, follow = Seq(MY_TWITTER_ID))(printFilteredStatusTweet)

  // REST client to fetch all historical data for each user_id.
  // val restClient = new TwitterRestClient(consumerToken, accessToken)
  // val userTweets   = Await.result(restClient.userTimelineForUserId(user_id = MY_TWITTER_ID, count = 20), Duration.Inf).data

  // for (t <- userTweets) {
  //   println(t.text) // Prints out all of my previous tweets.
  // }
  // restClient.shutdown()


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
