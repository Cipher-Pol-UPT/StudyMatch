# Performance

We load tested one endpoint with Apache JMeter: Staff searching the student directory. The plan is `performance/student-directory.jmx`.

## Why this endpoint

`GET /students/?search=a&page=0` is how Staff finds a student, and it does real work on every request. It filters students by name, sorts them, pages them (20 per page), and for each student on the page it works out the attendance and the average grade from their enrolments. So one request is several database queries, not one, and it's the kind of endpoint that slows down first.

The demo data only has 13 students, and "a" matches 9 of them, so each answer has 9 rows (about 2.5 KB). With a real year's worth of students the page would be full at 20 rows, and each request would do roughly twice the per-student work, so these times are on the low side.

## How the plan works

Each virtual user logs in once and then repeats the search:

1. `GET /users/me` to get the `XSRF-TOKEN` cookie. It answers 401 because nobody is logged in yet, which is expected and not counted as an error.
2. `POST /users/login` as the demo staff account (`sofia.staff@studymatch.com`, `Demo1234`), with the token sent back in the `X-XSRF-TOKEN` header. The HTTP Cookie Manager keeps the session cookie after that.
3. `GET /students/?search=a&page=0`, as many times as `loops` says.

Every request has a Response Assertion on the status code (200, or 401 for the first one), so anything else counts as an error.

The number of users, the ramp-up and the loops are properties, so the same plan does both runs. It runs from the command line, without the GUI and without View Results Tree, which would use memory and skew the numbers:

```bash
cd performance
jmeter -n -t student-directory.jmx -Jusers=1 -Jrampup=1 -Jloops=50 -l baseline.jtl -e -o baseline-report
jmeter -n -t student-directory.jmx -Jusers=50 -Jrampup=10 -Jloops=200 -l load.jtl -e -o load-report
```

`-e -o` writes the HTML dashboard, which has the same numbers as the Aggregate Report. The `.jtl` files and reports aren't committed.

## Where it ran

Only against our own machine: the backend on `127.0.0.1:8080` (`./mvnw spring-boot:run`) with the demo data from `data.sql`, PostgreSQL in Docker, and JMeter on the same machine.

- MacBook with an Apple M4 Pro (12 cores), 24 GB of memory, macOS 26.5.2
- Java 21.0.11, JMeter 5.6.3, Spring Boot defaults (200 Tomcat threads, 10 database connections)
- 20 November 2026

Before the two runs we did a warm-up run of 50 requests that isn't counted. The first requests after starting the backend are slower while Java compiles the code, and that would have made the baseline look worse than it is.

## Results

Only the `GET /students/` requests, not the login.

| | Baseline | Load |
| --- | --- | --- |
| Users | 1 | 50 |
| Ramp-up | 1 s | 10 s |
| Requests per user | 50 | 200 |
| Requests in total | 50 | 10,000 |
| Average | 25.7 ms | 135.8 ms |
| Median | 25 ms | 148 ms |
| 95th percentile | 31.4 ms | 188 ms |
| Throughput | 38.4 requests/s | 274.7 requests/s |
| Errors | 0% | 0% |

In the load run, the users that were still running averaged 38 at a time, and all 50 at the peak. The run took 36.5 seconds.

## What changed and why

With one user, the time of a request is just the work the request does: about 26 ms. The throughput (38 per second) is the same number seen the other way round, since one user waits for each answer before sending the next.

With 50 users, the throughput went up about 7 times, to 275 per second, so the backend does handle more than one request at a time. It didn't go up 50 times, though, and the average time went from 26 ms to 136 ms (the 95th percentile from 31 to 188 ms). Somewhere the requests are queuing. The numbers agree with each other: 275 requests per second that take 0.136 s each means about 37 requests in the system at once, the same as the 38 active users JMeter counted. So at this point the backend is full, and each extra user adds waiting time instead of throughput. The requests that ran while all 50 users were active averaged 176 ms.

Our best guess for the queue is the database connections. Spring Boot gives us 10 by default, while Tomcat accepts up to 200 requests at a time, so with 50 users about 40 requests wait for a connection. Each request also holds its connection for several queries, because the attendance and average grade are worked out per student on the page. We haven't measured either of these separately, so they're the next things to look at: a bigger connection pool, or fetching the attendance and grades for the whole page in one query.

Even so, no request failed, and 95% of them answered in under 0.2 s with 50 users searching non-stop, which is far more than Staff will ever do at once.

## The first load run didn't count

Our first try at the load run used 20 requests per user instead of 200. Each user finished its 20 requests in about half a second, before most of the others had even started, so there were never more than 4 users at the same time and the numbers looked almost like the baseline. Checking how many users were active (the `allThreads` column in the `.jtl`) showed it. With 200 requests per user, every user is still running when the last one starts.

## Costs we know about but haven't measured

The chat list loads each group chat whole, picture included, only to say whether it has one. A picture can be about 1.3 MB of base64, the same as a profile photo on `Student`. Opening or closing the app also loads the members of each of the user's chats one query at a time, to tell them about the online status. A group chat also sends each message, edit, delete and typing frame once per member, up to 20 times, where a DM sends it once to the topic. That's the price of a removed member hearing nothing from the moment they're out. None of this is a problem at our size. If the first two become one, the fix is a query that returns only whether the picture is there, and one that fetches all the members at once.
