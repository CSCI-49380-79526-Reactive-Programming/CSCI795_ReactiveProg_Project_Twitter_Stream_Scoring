import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props

// Twitter4s streaming library
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.TwitterStreamingClient

class HelloActor extends Actor {
  def receive = {
    case "hello"  => println("hello back at you")
    case s:String => println("Received message: " + s)
    case n:Int    => println("Received integer: " + n)
    case _        => println("huh?")
  }
}

object Main extends App {
  val system = ActorSystem("DemoSystem")
  val helloActor = system.actorOf(Props[HelloActor], name = "helloactor")

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

}

