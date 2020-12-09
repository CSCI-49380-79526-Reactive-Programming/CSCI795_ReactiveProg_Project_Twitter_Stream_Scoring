import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import com.github.tototoshi.csv._
import java.io.File
import java.time._

// Twitter4s streaming library
import com.danielasfregola.twitter4s.entities._
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.TwitterStreamingClient

class HelloActor extends Actor {
  def receive = {
    case "hello"  => println("hello back at you")
                     println(sender)

    case s:String => println("Received message: " + s)
    case n:Int    => println("Received integer: " + n)
    case _        => println("huh?")
  }
}


class Critic extends Actor {

  val scores = new scala.collection.mutable.HashMap[String, TwitterScoring]

  def receive = {
    case (politician: String, tweet: Tweet) =>
      if (scores(politician).update(tweet)) {
        // TODO update TUI/GUI
      }
  }
}


class TwitterScoring {
  // Two sets of tweet IDs
  val disputed   = new scala.collection.mutable.HashSet[Long]
  val undisputed = new scala.collection.mutable.HashSet[Long]

  def update(tweet : Tweet) : Boolean = {
    val id      = tweet.id
    val flagged = !tweet.withheld_in_countries.isEmpty
    if (disputed contains id) {
      // We already processed this tweet as disputed
      if (!flagged) {
        // However it's status has changed to undisputed
        disputed   - id
        undisputed + id
        return true
       }
       // And no change is required
    }
    else if (undisputed contains id) {
      // We processed this tweet as undisputed,
      if (flagged) {
        // However it's status has changed to disputed
        disputed + id
        undisputed - id
        return true
      }
      // And no change is required
    }
    else {
      // We have *not* processed this tweet
      if (flagged) {
        disputed + id
      }
      else {
        undisputed - id
      }
      return true
    }
    return false
  }
  
  def pinochiccoScore() : Double = {
    disputed.size / (disputed.size + undisputed.size)
  }

}

class Politician( val updater    : ActorRef
                , val name       : String
                , val party      : String
                , val state      : String
                , val twitter    : String
                , val term_start : Instant
                , val term_end   : Instant
                ) extends Actor {

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

object Main extends App {
  val senatorsFile = "us-senate.csv" 
  val system       = ActorSystem("DemoSystem")
  val helloActor   = system.actorOf(Props[HelloActor], name = "helloactor")
  val critic       = system.actorOf(Props[Critic], name = "critic")

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

  // println('A')

  println('A')
  helloActor ! "hello" // String "hello"
  println('B')
  helloActor ! "Some other message other than hello" // String
  println('C')
  helloActor ! 1234 // Int
  println('D')
  helloActor ! 'c' // Char
  println('E')
  helloActor ! helloActor
  println('F')

  def addPolitician(system : ActorSystem)(updater : ActorRef )(dataRow : Seq[String]) : Unit = {
    // Convert Seq to VEctor for more efficient random access
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
    println("Adding " + name)
    system.actorOf(Props(new Politician(updater, name, party, state, twitter, begin, finish)), name = id)
  }

}
