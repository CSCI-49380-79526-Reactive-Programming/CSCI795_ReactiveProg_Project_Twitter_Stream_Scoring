# CSCI 795 Reactive Programming<br>Hunter College FA'20

## Twitter Stream Scoring Project
### Overview
Using Scala and the Akka actor framework, this project aims to demonstrate reactive programming principles/concepts
by using Akka actors to process a live streaming data feed of Tweets from Twitter's API.

This project will track the Tweets (historical and live) of 100 senators, and score their Tweets based on a
sentiment analysis word list that was published by a another research project (http://www2.imm.dtu.dk/pubdb/pubs/6010-full.html),
and generate a "positivity" score of these tweets.

The results will then be displayed via a GUI table that will update in real-time based on tweets that we
receive for the various politicians that we are tracking/scoring.

## Dev Setup
### Pre-reqs
1. Sign up for a Twitter Dev Account (Takes 2-3 days for approval)
    1. This will give you the account level token pair `<API_KEY>` and `<API_TOKEN>`
2. Create an app in your dev portal/account.
    1. Then generate an app level access token pair for the `<APP_ACCESS_TOKEN>` and `<APP_ACCESS_KEY>`
3. Add these tokens in the `secrets.csv` file as outlined in the steps below.

### Run Instructions
1. Checkout this repo.
2. `cd` into the root of this project
3. In the provided `secrets.csv` file in the project folder, fill in the necessary tokens that you generate in the previous pre-req steps.
4. Run `./sbt` to run the build tool.
5. Run `reStart` to run the application and hot restart the app after code changes.

- Notes: Current API streaming service only supports limited # of connections over a time interval. If you run into any 400/500 errors regarding connection threshold, simply stop the program and try again after a few minutes.

### Milestones
#### @y3pio (https://github.com/y3pio):
- [X] Set up Akka dev environment and setup instructions
  - [X] Import this library: https://github.com/DanielaSfregola/twitter4s
- [X] Connect to Twitter API
  - [X] Need to create DEV account and sign up
- [X] Get Akka stream to process live Tweet updates
  - [X] Need a dummy account, use the DEV account for this?

#### @recursion-ninja (https://github.com/recursion-ninja):
- [X] Get historical tweets, not just processing live ones that come in.
- [ ] ~~Figure out how to parse metadata of tweet both present/historical (flagged content - need to simulate this)~~
  - (This information is not available via the Twitter API)
- [X] Collect politician lists (who we are going to track)
- [X] Come up with scoring system and print to output

Stretch goal:
- [X] Create Akka stream to log and process further back into history
- [X] Create UI to show data (plots, graphs, other fancy UI stuff)

## Actor Diagram
![Diagram](./CSCI795_Reactive_Prog_Final_Project_Diagram.png) 
