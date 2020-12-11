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

// Our own classes
import artist.Artist
import politician.{Politician, PoliticianKey, PoliticianRow}
import scorer.Critic
import sentinel.Sentinel
import tweet.TwitterSecrets

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
  val senatorsFile = "us-senate.csv"
  val stream  = CSVReader.open(new File(senatorsFile)).iterator
  stream.next // Drop the header row from the stream iterator

  // Add politician actors to system and collect their keys
  val politiciansToStream = stream.map(addPolitician(system)(critic))

  system.actorOf(Props(new Sentinel(secrets, critic, politiciansToStream)))

  /*
    addPolitician

    Adds a politician into our actor system to update our GUI and to fetch historical tweets
    Returns the politician's key
  */
  def addPolitician(system : ActorSystem)(updater : ActorRef)(dataRow : Seq[String]) : PoliticianKey = {
    val vec     = dataRow.toVector
    val name    = vec(15)
    val party   = vec(14)
    val state   = vec( 0)
    val twitter = vec(48)
    val begin   = Instant.parse(vec(28) + "T00:00:00.00Z")
    val finish  = Instant.parse(vec(29) + "T00:00:00.00Z")
    val key     = new PoliticianKey(name, party, state, twitter)
    system.actorOf(Props(new Politician(secrets, critic, key, begin, finish)))
    key
  }

  def constructGUI(rows : ObservableBuffer[PoliticianRow]) : PrimaryStage = {
    new PrimaryStage {
        title.value = "CSCI-795 — Politician Positivity Presentation"
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
                prefWidth = 90
              },
              new TableColumn[PoliticianRow, String] {
                text = "Pinocchio"
                cellValueFactory = { _.value.pinocchio }
                prefWidth = 90
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
