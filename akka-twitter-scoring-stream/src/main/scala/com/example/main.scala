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
import scala.concurrent.Await
import scala.concurrent.duration._

// Our own classes
import artist.{Artist}
import politician.{Politician, PoliticianKey, PoliticianRow}
import scorer.{Critic}
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
  val artist = system.actorOf(Props(new Artist(rows)), name = "artist")
  val critic = system.actorOf(Props(new Critic(wordRanks, artist)), name = "critic")

  // Get the Twitter authentication secrets
  val secretsFile = "secrets.csv"
  val secrets = new TwitterSecrets(secretsFile)

  // Get the politician input data
  val senatorsFile = "us-senate-test.csv"
  val stream  = CSVReader.open(new File(senatorsFile)).iterator
  stream.next // Drop the header row from the stream iterator
  
  // Add all senators as actors to our system and set up stream
  var politiciansToStream: Seq[PoliticianKey] = Seq()
  for(s <- stream) {
    val vec = s.toVector
    val name    = vec(15)
    val party   = vec(14)
    val state   = vec( 0)
    val twitter = vec(48)
    politiciansToStream = politiciansToStream :+ new PoliticianKey(name, party, state, twitter)
    addPolitician(system)(critic)(s)
  }

  // Connect to stream once we have all the twitter handlers to follow
  connectToPoliticianTwitterStreams(politiciansToStream)(critic)

  /*
    connectToPoliticianTwitterStreams

    Fetches IDs for all twitter handles to follow
    Starts a single streaming connection that,
    listens for and updates for all the IDs being followed
  */
  def connectToPoliticianTwitterStreams(politiciansToStream: Seq[PoliticianKey])(updater: ActorRef) = {
    val (consumerToken, accessToken) = secrets.getTokens()
    val restClient      = TwitterRestClient(consumerToken, accessToken)
    val streamingClient = TwitterStreamingClient(consumerToken, accessToken)

    var twitterIDsToStream: Seq[Long] = Seq()
    politiciansToStream.foreach((p: PoliticianKey) => {
      // println("Connecting to live twitter stream: @" + p.twitter_handle)
      val userData = Await.result(restClient.user(screen_name = p.twitter_handle), Duration.Inf).data
      println("Connecting to live twitter stream: @" + p.twitter_handle + " - ID: " + userData.id)
      twitterIDsToStream = twitterIDsToStream :+ userData.id
    })

    restClient.shutdown()

    // Function to process incoming tweet stream
    def printFilteredStatusTweet: PartialFunction[StreamingMessage, Unit] = {
      case tweet: Tweet => {
        val receiveTweetUserId = tweet.user.get.id
        val receiveTweetUserScreenName = tweet.user.get.screen_name
        println("\n@" + receiveTweetUserScreenName + " (" + receiveTweetUserId + ") : " + tweet.text + "\n")

        // Send below tweet to critic
        val newTweet = new Tweet(created_at=Instant.now(),id=tweet.id, id_str=tweet.id_str, source="", text=tweet.text)
        
        // We don't really care about the other key fields (used for display) to update.
        val key = new PoliticianKey("_", "_", "_", receiveTweetUserScreenName)
        updater ! (key, newTweet)
      }
    }
    // Start streaming for incoming tweets.
    streamingClient.filterStatuses(stall_warnings = true, follow = twitterIDsToStream)(printFilteredStatusTweet)
  }

  /*
    addPolitician

    Adds a politician into our actor system to update our GUI and to fetch historical tweets
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
