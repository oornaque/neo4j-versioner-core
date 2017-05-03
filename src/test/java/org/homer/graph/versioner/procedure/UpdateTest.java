package org.homer.graph.versioner.procedure;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.v1.*;
import org.neo4j.harness.junit.Neo4jRule;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Created by marco.falcier on 28/04/17.
 */
public class UpdateTest {
    @Rule
    public Neo4jRule neo4j = new Neo4jRule()

            // This is the function we want to test
            .withProcedure(Update.class);

    @Test
    public void shouldCreateANewStateWithoutAdditionalLabelAndDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            // Given
            Session session = driver.session();
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:593910000000}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATUS {startDate:593910000000}]->(s)");

            // When
            StatementResult result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.update(e, 'context', {key:'newValue'}) YIELD id RETURN id");
            StatementResult countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            StatementResult nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            StatementResult correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");

            // Then
            assertThat(result.single().get("id").asLong(), equalTo(2l));
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2l));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1l));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0l));
        }
    }

    @Test
    public void shouldCreateANewStateWithAdditionalLabelButWithoutDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            // Given
            Session session = driver.session();
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:593910000000}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATUS {startDate:593910000000}]->(s)");

            // When
            StatementResult result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.update(e, 'context', {key:'newValue'}, 'Error') YIELD id RETURN id");
            StatementResult countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            StatementResult nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            StatementResult correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");
            StatementResult currentStateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s) return s");

            // Then
            assertThat(result.single().get("id").asLong(), equalTo(2l));
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2l));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1l));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0l));
            assertThat(currentStateResult.single().get("s").asNode().hasLabel("Error"), equalTo(true));
        }
    }

    @Test
    public void shouldCreateANewStateWithAdditionalLabelAndDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            // Given
            Session session = driver.session();
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:593910000000}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATUS {startDate:593910000000}]->(s)");

            // When
            StatementResult result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.update(e, 'context', {key:'newValue'}, 'Error', 593920000000) YIELD id RETURN id");
            StatementResult countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            StatementResult nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            StatementResult correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");
            StatementResult currentStateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s) return s");
            StatementResult dateResult = session.run("MATCH (e:Entity)-[r:CURRENT]->(s) RETURN r.date as relDate");
            StatementResult hasStatusDateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s:State)-[:PREVIOUS]->(s2:State)<-[rel:HAS_STATUS]-(e) RETURN rel.endDate as endDate");

            // Then
            assertThat(result.single().get("id").asLong(), equalTo(2l));
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2l));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1l));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0l));
            assertThat(currentStateResult.single().get("s").asNode().hasLabel("Error"), equalTo(true));
            assertThat(dateResult.single().get("relDate").asLong(), equalTo(593920000000l));
            assertThat(hasStatusDateResult.single().get("endDate").asLong(), equalTo(593920000000l));
        }
    }

    @Test
    public void shouldCreateANewStateFromAnEntityWithoutAState() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            // Given
            Session session = driver.session();
            session.run("CREATE (e:Entity {key:'immutableValue'})");

            // When
            StatementResult result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.update(e, 'context', {key:'newValue'}, 'Error', 593920000000) YIELD id RETURN id");
            StatementResult correctResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) RETURN id(s) as stateId");

            // Then
            assertThat(result.single().get("id").asLong(), equalTo(1l));
            assertThat(correctResult.single().get("stateId").asLong(), equalTo(1l));
        }
    }
}
