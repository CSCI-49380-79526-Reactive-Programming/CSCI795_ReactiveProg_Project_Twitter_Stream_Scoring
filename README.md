# CSCI795_ReactiveProg_Project
Presentation for CSCI 795 Reactive Programming Course - Hunter College FA'20

## Run Instructions
1. Checkout this repo.
2. `cd` into the root of this project
3. Run `./sbt` to run the build tool.
4. Run `reStart` to run the application and hot restart the app after code changes.

## TODO:
### Ye:
- Set up Akka dev environment and setup instructions [DONE]
  - Import this library: https://github.com/DanielaSfregola/twitter4s [DONE]
- Connect to Twitter API
  - Need to create DEV account and sign up
- Get Akka stream to process live Tweet updates
  - Need a dummy account, use the DEV account for this?

### Alex:
- Get historical tweets, not just processing live ones that come in.
- Figure out how to parse metadata of tweet both present/historical (flagged content - need to simulate this)
- Collect politician lists (who we are going to track)
- Come up with scoring system and print to output

Stretch goal:
- Create Akka stream to log and process further back into history
- Create UI to show data (plots, graphs, other fancy UI stuff)
