# Github contributions ⌨ with cats-effect

This project is a playground for learning FP and particularly `cats-effect` library. The code solves the problem 👇

## Original task

The program should:
* Take username, as a command line argument.
* When started, output all public non-fork repositories of the user, and for each repository -- list contributors (ignoring the user herself) ordered by the number of contributions per contributor in descending order.
* Use GitHub REST API.

Choose any language you prefer. Treat the code as if it goes straight into production.

## Running the app

`GH_TOKEN=xxxxxxxxxx sbt run`

## Extra considerations

* There is also need to get a github token. We fetch it from en env variable `GH_TOKEN`.
* The next page URI is available in one of the `Link` headers.
