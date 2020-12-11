package scorer

import akka.actor.{Actor, ActorRef}
import com.danielasfregola.twitter4s.entities.Tweet

import scalafx.collections.ObservableBuffer

import politician.{PoliticianKey, PoliticianRow}
import tweet.TwitterScoring

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
               val row = getRow(politician).get
               row.positivity.value = x.toString
               row.pinocchio.value  = y.toString
             case (Some(x), None) =>
               // A score was updated,
               // We need to update the GUI
               val row = getRow(politician).get
               row.positivity.value = x.toString
             case (None, Some(y)) =>
               // A score was updated,
               // We need to update the GUI
               val row = getRow(politician).get
               row.pinocchio.value  = y.toString
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

  def getRow(politician : PoliticianKey) : Option[PoliticianRow] = {
    rows foreach (x => if (x.equals(politician)) { return Some(x) })
    return None
  }

}