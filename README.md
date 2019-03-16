# Github contributions with cats-effect

This project is a playground for learning FP and particularly `cats-effect` library. The code solves the problem 👇

## Original task

A new Amsterdam-based team has decided to address a problem #1 all developers in the world have: keeping CV up-to-date. As a startup with lean mentality they decided to start small and in the first version support retrieving relevant information from GitHub and displaying it in the terminal.

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
