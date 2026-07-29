# RailLens Backend

RailLens Backend is a Spring Boot REST API that powers the RailLens railway information system. It provides endpoints for searching trains, viewing train details, exploring stations, and finding trains running between two stations.

## Tech Stack

* Java 21
* Spring Boot
* Spring Data JPA
* PostgreSQL
* Maven

## Features

* Train Search
* Train Details
* Station Search
* Station Details
* Trains Between Stations
* Journey Timeline
* RESTful API
* PostgreSQL persistence

## Project Structure

```
src/main/java/com/labs/train/train_db
├── controller
├── service
├── repository
├── entity
├── model
├── exception
├── config
└── common
```

## Getting Started

### Prerequisites

* Java 21+
* PostgreSQL
* Maven

### Database

Create a PostgreSQL database named:

```
traindb
```

Update `application.properties` with your database credentials.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/traindb
spring.datasource.username=postgres
spring.datasource.password=your_password
```

### Run the Application

```bash
mvn spring-boot:run
```

or

```bash
./mvnw spring-boot:run
```

The API will start on:

```
http://localhost:8080
```

## API Endpoints

### Train Search

```
GET /api/v1/trains/search?q={query}
```

### Train Details

```
GET /api/v1/trains/{trainNumber}
```

### Station Search

```
GET /api/v1/stations/search?q={query}
```

### Station Details

```
GET /api/v1/stations/{stationCode}
```

### Trains Between Stations

```
GET /api/v1/journeys?from=LTT&to=PPTA
```

Returns:

* Source station
* Destination station
* Total matching trains
* Departure time
* Arrival time
* Journey duration
* Distance

## Architecture

The application follows a layered architecture:

```
Controller
      ↓
Service
      ↓
Repository
      ↓
PostgreSQL
```

Journey search is implemented in a dedicated `JourneyService` to keep train and station logic isolated.

## Future Roadmap

* Live Running Status
* PNR Status
* Seat Availability
* Platform Information
* Coach Position
* Delay Information

## License

This project is intended for learning and personal development.
