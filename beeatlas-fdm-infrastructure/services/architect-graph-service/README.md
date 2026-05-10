# Architect Graph Service

A Spring Boot microservice for managing and visualizing architectural graphs using Neo4j. This service provides REST APIs for creating, querying, and comparing architectural diagrams and system relationships based on the C4 model.

## Features

- **Graph Management**: Create and manage local and global architectural graphs
- **Diagram Generation**: Generate various types of C4 model diagrams (Context, Container, Deployment)
- **System Search**: Search for software systems, containers, and deployment nodes
- **Version Comparison**: Compare different versions of systems
- **Influence Analysis**: Analyze relationships and dependencies between systems
- **Neo4j Integration**: Full integration with Neo4j graph database
- **RESTful API**: Comprehensive REST API with Swagger/OpenAPI documentation
- **DOT Format Support**: Export diagrams in Graphviz DOT format

## Technology Stack

- **Java**: 17 (Eclipse Temurin)
- **Framework**: Spring Boot 2.7.3
- **Database**: Neo4j 5 Community Edition
- **Build Tool**: Maven 3.9
- **API Documentation**: Swagger/OpenAPI (SpringDoc)

## Prerequisites

- Docker and Docker Compose installed
- Java 17+ (for local development)
- Maven 3.9+ (for local development)

## Quick Start with Docker Compose

The easiest way to run the service is using Docker Compose, which will start both the application and Neo4j database:

```bash
# Build and start all services
docker-compose up --build

# Or run in detached mode
docker-compose up -d --build
```

This will start:
- **Architect Graph Service** on `http://localhost:8080`
- **Neo4j Database** on `bolt://localhost:7687`
- **Neo4j Browser** (web interface) on `http://localhost:7474`

### Accessing Neo4j Browser

1. Open your browser and navigate to `http://localhost:7474`
2. Use the following credentials:
   - **Username**: `neo4j`
   - **Password**: `password`

### Stopping the Services

```bash
# Stop services
docker-compose down

# Stop and remove volumes (this will delete all data)
docker-compose down -v
```

## API Documentation

Once the service is running, you can access the Swagger API documentation at:

```
http://localhost:8080/swagger-ui.html
```

Or the OpenAPI JSON specification at:

```
http://localhost:8080/v3/api-docs
```

## API Endpoints

### Graph Operations

#### Create Local Graph
```bash
POST /api/v1/graph/local/json
Content-Type: application/json

{
  "workspace": { ... }
}
```
Creates a local graph from JSON document. All vertices and relationships are tagged with `graphTag: Local`.

#### Create Global Graph
```bash
POST /api/v1/graph/json
Content-Type: application/json

{
  "workspace": { ... }
}
```
Adds a system from the specified JSON document to the global graph. All vertices and relationships are tagged with `graphTag: Global`.

#### Get Graph Status
```bash
GET /api/v1/graph/{graph-type}/task/{task-id}
```
Retrieves the status of a graph by task key and graph type.

### Search Operations

#### Search Deployment Nodes
```bash
GET /api/v1/search/deployment-node?search={query}
```

#### Search Containers
```bash
GET /api/v1/search/container?search={query}
```

#### Search Software Systems
```bash
GET /api/v1/search/software-system?search={query}
```

### Diagram Generation

#### Context Diagram
```bash
GET /api/v1/context/{softwareSystemMnemonic}?rankDirection={direction}
GET /api/v1/diagram/context?cmdb={cmdb}&rankDirection={direction}&communicationDirection={direction}
```

#### Container Diagram
```bash
GET /api/v1/context/{softwareSystemMnemonic}/{containerMnemonic}?rankDirection={direction}
```

#### Deployment Diagram
```bash
GET /api/v1/deployment/{environment}/{softwareSystemMnemonic}?rankDirection={direction}
GET /api/v1/diagram/deployment?cmdb={cmdb}&env={env}&rank-direction={direction}&deployment-name={name}
```

#### DOT Format Diagrams
```bash
GET /api/v1/diagram/dot?id={id}
GET /api/v1/context/dot?cmdb={cmdb}
GET /api/v1/context/influence/dot?cmdb={cmdb}
GET /api/v1/influence/dot?id={id}
```

### Influence Analysis

#### Get Container Influence
```bash
GET /api/v1/influence?cmdb={cmdb}&name={name}
```

#### Get Product Influence
```bash
GET /api/v1/graph/product/{cmdb}/influence
```

#### Get Deployment Influence
```bash
GET /api/v1/graph/deployment/{cmdb}/influence?name={name}&env={env}
```

### Version Comparison

#### Compare Two Versions
```bash
GET /api/v1/diff/{cmdb}/{firstVersion}/{secondVersion}
```

#### Compare with Current Version
```bash
GET /api/v1/diff/{cmdb}/{firstVersion}
```

### Custom Cypher Queries

#### Execute Custom Query
```bash
GET /api/v1/elements
Header: CYPHER-QUERY: {cypher_query}
```

## Configuration

### Environment Variables

The service can be configured using the following environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_NEO4J_URI` | Neo4j connection URI | `bolt://neo4j:7687` |
| `SPRING_NEO4J_AUTHENTICATION_USERNAME` | Neo4j username | `neo4j` |
| `SPRING_NEO4J_AUTHENTICATION_PASSWORD` | Neo4j password | `password` |
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m` |

### Application Properties

Key configuration in `application.properties`:

```properties
spring.application.name=architect-graph
spring.services.graphviz.url=https://structurizr.vimpelcom.ru/graphviz
server.tomcat.relaxed-query-chars=[,]
```

## Local Development

### Building the Project

```bash
# Build the project
mvn clean package

# Skip tests
mvn clean package -DskipTests
```

### Running Locally

1. Start Neo4j database (using Docker):
```bash
docker run -d \
  --name neo4j \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password \
  neo4j:5-community
```

2. Update `application.properties` or set environment variables:
```bash
export SPRING_NEO4J_URI=bolt://localhost:7687
export SPRING_NEO4J_AUTHENTICATION_USERNAME=neo4j
export SPRING_NEO4J_AUTHENTICATION_PASSWORD=password
```

3. Run the application:
```bash
mvn spring-boot:run
```

Or run the JAR directly:
```bash
java -jar target/architecting_graph-*.jar
```

## Docker

### Building the Docker Image

```bash
docker build -t architect-graph-service:latest .
```

### Running the Docker Container

```bash
docker run -d \
  --name architect-graph-service \
  -p 8080:8080 \
  -e SPRING_NEO4J_URI=bolt://host.docker.internal:7687 \
  -e SPRING_NEO4J_AUTHENTICATION_USERNAME=neo4j \
  -e SPRING_NEO4J_AUTHENTICATION_PASSWORD=password \
  architect-graph-service:latest
```

## Project Structure

```
architect-graph-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── ru/beeline/architecting_graph/
│   │   │       ├── client/          # External service clients
│   │   │       ├── config/          # Spring configuration
│   │   │       ├── controller/      # REST controllers
│   │   │       ├── consumer/        # Message consumers
│   │   │       ├── dto/             # Data transfer objects
│   │   │       ├── exception/       # Custom exceptions
│   │   │       ├── model/           # Domain models
│   │   │       ├── repository/      # Neo4j repositories
│   │   │       ├── service/         # Business logic
│   │   │       └── utils/           # Utility classes
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── Dockerfile                        # Production Dockerfile
├── docker-compose.yml               # Docker Compose configuration
├── pom.xml                          # Maven configuration
└── README.md                        # This file
```
## Graph Model (Graph Model)

The model is based on a graph data structure where main entities are connected by relationships.

#### Main entities of the graph model:

1. **Software System** - Software System
   - Central entity representing a software system
   - Connected to containers, components, and infrastructure

2. **Container** - Container
   - Represents containerized applications
   - Connected to container instances and deployment nodes

3. **Component** - Component
   - Architectural components of the system
   - Connected to containers and interfaces

4. **Container Instance** - Container Instance
   - Specific running instances of containers
   - Connected to infrastructure nodes

5. **Infrastructure Node** - Infrastructure Node
   - Physical or virtual infrastructure nodes
   - Connected to deployment nodes

6. **Deployment Node** - Deployment Node
   - Target nodes for application deployment
   - Connected to deployment environments

7. **Deployment Environment** - Deployment Environment
   - Environments (development, testing, production)
   - Connected to deployment nodes

#### Relationship types:

- **Child** - Hierarchical relationships (parent-child)
- **Deploy** - Deployment relationships
- **Relationship** - Various types of connections between entities

### Connections between different elements

| Label | Connected elements (Child) | Connected elements (Relationship) | Connected elements (Deploy) |
|-------|----------------------------|-----------------------------------|-----------------------------|
| Software System|Container|Software System, Container, Component|Deployment Environment|
| Container|Component|Software System, Container, Component|Container Instance|
| Component|Component|Software System, Container, Component||
| Container Instance||||
| Infrastructure Node||Deployment Node, Infrastructure Node||
| Deployment Node|Deployment Node, Infrastructure Node, Container Instance|Deployment Node,Infrastructure Node||
| Deployment Environment|Deployment Node, Infrastructure Node|||

---

## Examples

### Example 1: Create a Local Graph

```bash
curl -X POST http://localhost:8080/api/v1/graph/local/json \
  -H "Content-Type: application/json" \
  -d '{
    "workspace": {
      "model": {
        "softwareSystems": [...]
      }
    }
  }'
```

### Example 2: Search for Software Systems

```bash
curl "http://localhost:8080/api/v1/search/software-system?search=payment"
```

### Example 3: Generate Context Diagram

```bash
curl "http://localhost:8080/api/v1/context/payment-system?rankDirection=TB"
```

### Example 4: Get Product Influence

```bash
curl "http://localhost:8080/api/v1/graph/product/PAYMENT-SYS/influence"
```

### Example 5: Compare Versions

```bash
curl "http://localhost:8080/api/v1/diff/PAYMENT-SYS/1/2"
```

### Example 6: Get Context Diagram in DOT Format

```bash
curl "http://localhost:8080/api/v1/context/dot?cmdb=PAYMENT-SYS"
```

## Troubleshooting

### Service won't start

1. Check if Neo4j is running and accessible:
```bash
docker ps | grep neo4j
```

2. Verify Neo4j connection:
```bash
docker exec -it architect-graph-neo4j cypher-shell -u neo4j -p password
```

3. Check application logs:
```bash
docker logs architect-graph-service
```

### Connection Issues

- Ensure Neo4j is healthy before starting the application
- Verify environment variables are set correctly
- Check network connectivity between containers

### Build Issues

- Ensure Docker has enough memory allocated (at least 2GB recommended)
- Check internet connection for Maven dependency downloads
- Verify Java version compatibility (Java 17 required)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

For issues and questions, please create an issue in the project repository.
