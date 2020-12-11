package artist

import akka.actor.{Actor, ActorRef}
import com.danielasfregola.twitter4s.entities.Tweet

import scalafx.collections.ObservableBuffer

import politician.{PoliticianKey, PoliticianRow, PoliticianUpdate}

// Actor who listens for updates to the GUI
class Artist(rows_ : ObservableBuffer[PoliticianRow]) extends Actor {

  private var rows = rows_

  def receive = {
    case (update: PoliticianUpdate) =>
      val row = getRow(update.key) match {
          case Some(row) => row
          case None =>
            // Politician is new
            // New to add them to the scoring and the GUI
            val x = new PoliticianRow(update.key, 0, 0)
            rows.append(x)
            x
      }
      
      // Update politician's row in GUI 
      update.positivity match {
          case None => ()
          case Some(positivity) =>
            row.positivity.value = renderScore(positivity)
      }
      update.pinocchio match {
          case None => ()
          case Some(pinocchio) =>
            row.pinocchio.value = renderScore(pinocchio)
      }
  }

  def getRow(politician : PoliticianKey) : Option[PoliticianRow] = {
    rows foreach (x => if (x.twitter_handle.equals(politician.twitter_handle)) { return Some(x) })
    return None
  }

  def renderScore(score : Double) : String = {
    val percent = score * 100
    val x = percent.abs
    var prefix = ""

    if ( percent > 0)
      prefix += " "
    if (x < 100)
      prefix += " "
    if (x < 10)
      prefix += " "

    prefix + f"$percent%1.3f" + "%"
  }

}