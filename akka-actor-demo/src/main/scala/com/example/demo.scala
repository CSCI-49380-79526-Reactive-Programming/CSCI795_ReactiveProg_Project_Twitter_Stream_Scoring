import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props

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

  // println('A')

  helloActor ! "hello" // String "hello"
  helloActor ! "Some other message other than hello" // String
  helloActor ! 1234 // Int
  helloActor ! 'c' // Char

}

