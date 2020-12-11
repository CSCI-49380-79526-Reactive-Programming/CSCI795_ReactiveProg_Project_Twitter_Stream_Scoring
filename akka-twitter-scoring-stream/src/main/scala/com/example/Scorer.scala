package scorer

import akka.actor.{Actor, ActorRef}
import com.danielasfregola.twitter4s.entities.Tweet

import scalafx.collections.ObservableBuffer

import artist.Artist
import politician.{PoliticianKey, PoliticianRow, PoliticianUpdate}
import tweet.TwitterScoring

// Actor who maintains the scoring of all politicians and updates the GUI.
// Listens for updates from the Politician actors for new Tweets to be processed.
class Critic(wordRanking_ : Map[String,Int], artist_ : ActorRef) extends Actor {

  private val scoring   = new scala.collection.mutable.HashMap[String, TwitterScoring]
  private var artist    = artist_
  private var wordRanks = wordRanking_

  def receive = {
    case (politician: PoliticianKey, tweet: Tweet) =>
       scoring.get(politician.twitter_handle) match {
         case None =>
           // Politician is new
           // New to add them to the scoring and the GUI
           scoring.addOne(politician.twitter_handle, new TwitterScoring(wordRanks, tweet))
           val positivity = scoring(politician.twitter_handle).positivityScore
           val pinocchio  = scoring(politician.twitter_handle).pinocchioScore
           artist ! new PoliticianUpdate(politician, Some(positivity), Some(pinocchio))
           
         case Some(twitterScore) =>
           // We already have a score for this politician
           // We update the state
           twitterScore.update(tweet) match {
             case (None, None) => () // Nothing to update
             case (x,y)        => artist ! new PoliticianUpdate(politician, x, y)
           }
       }
  }

}