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

