package org.homer.versioner.core.procedure;

import org.apache.commons.lang3.StringUtils;
import org.homer.versioner.core.builders.UpdateBuilder;
import org.homer.versioner.core.core.CoreProcedure;
import org.homer.versioner.core.exception.VersionerCoreException;
import org.homer.versioner.core.output.BooleanOutput;
import org.homer.versioner.core.output.RelationshipOutput;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.homer.versioner.core.Utility.*;

/**
 * RelationshipProcedure class, it contains all the Procedures needed to create versioned relationships between Entities
 */
public class RelationshipProcedure extends CoreProcedure {

    @Procedure(value = "graph.versioner.relationships.create", mode = Mode.WRITE)
    @Description("graph.versioner.relationships.create(entityA, entitiesB, type, relProps, date) - Create multiple relationships from entitySource to each of the entityDestinations with the given type and/or properties for the specified date.")
    public Stream<RelationshipOutput> relationshipsCreate(
            @Name("entitySource") Node entitySource,
            @Name("entityDestinations") List<Node> entityDestinations,
            @Name(value = "relProps", defaultValue = "[{}]") List<Map<String, Object>> relProps,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        Optional<Node> sourceCurrentState = createNewSourceState(entitySource, defaultToNow(date));
        isEntityOrThrowException(entitySource);

        return entityDestinations.size() == relProps.size() ? sourceCurrentState.map(node -> zip(entityDestinations, relProps).stream().map((item) -> {
            final Node destinationNode = item.getLeft();
            isEntityOrThrowException(destinationNode);
            Optional<Node> destinationRNode = getRNode(destinationNode);
            Map<String, Object> props = item.getRight();

            if (destinationRNode.isPresent())
                return streamOfRelationships(createRelationship(node, destinationRNode.get(), props.get("label") instanceof String ? ((String) props.get("label")) : "TYPE_UNDEFINED", props));

            return Stream.<RelationshipOutput>empty();

        }).reduce(Stream::concat).orElseGet(Stream::empty)).orElseGet(Stream::empty) : Stream.empty();
    }

    @Procedure(value = "graph.versioner.relationship.create", mode = Mode.WRITE)
    @Description("graph.versioner.relationship.create(entityA, entityB, type, relProps, date) - Create a relationship from entitySource to entityDestination with the given type and/or properties for the specified date.")
    public Stream<RelationshipOutput> relationshipCreate(
            @Name("entitySource") Node entitySource,
            @Name("entityDestination") Node entityDestination,
            @Name(value = "type") String type,
            @Name(value = "relProps", defaultValue = "{}") Map<String, Object> relProps,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        isEntityOrThrowException(entitySource);
        isEntityOrThrowException(entityDestination);

        Optional<Node> sourceCurrentState = createNewSourceState(entitySource, defaultToNow(date));
        Optional<Node> destinationRNode = getRNode(entityDestination);

        if (sourceCurrentState.isPresent() && destinationRNode.isPresent()) {
            return streamOfRelationships(createRelationship(sourceCurrentState.get(), destinationRNode.get(), type, relProps));
        } else {
            return Stream.empty();
        }
    }

    @Procedure(value = "graph.versioner.relationship.delete", mode = Mode.WRITE)
    @Description("graph.versioner.relationship.delete(entityA, entityB, type, date) - Delete a custom type relationship from entitySource's current State to entityDestination for the specified date.")
    public Stream<BooleanOutput> relationshipDelete(
            @Name("entitySource") Node entitySource,
            @Name("entityDestination") Node entityDestination,
            @Name(value = "type") String type,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        isEntityOrThrowException(entitySource);
        isEntityOrThrowException(entityDestination);
        if (isSystemType(type)) {
            throw new VersionerCoreException("It's not possible to delete a System Relationship like " + type + ".");
        }

        Optional<Node> sourceCurrentState = createNewSourceState(entitySource, defaultToNow(date));
        Optional<Node> destinationRNode = getRNode(entityDestination);

        Update updateProcedure = new UpdateBuilder().withLog(log).withDb(db).build().orElseThrow(() -> new VersionerCoreException("Unable to initialize update procedure"));

        if (sourceCurrentState.isPresent() && destinationRNode.isPresent()) {
            final long destId = destinationRNode.get().getId();
            updateProcedure.update(entitySource, sourceCurrentState.get().getAllProperties(), "", null);
            getCurrentRelationship(entitySource).ifPresent(rel -> rel.getEndNode().getRelationships(RelationshipType.withName(type), Direction.OUTGOING).forEach(rel2 -> {
                if (rel2.getEndNode().getId() == destId) {
                    rel2.delete();
                }
            }));
            return Stream.of(new BooleanOutput(Boolean.TRUE));
        } else {
            return Stream.of(new BooleanOutput(Boolean.FALSE));
        }
    }

    static Relationship createRelationship(Node source, Node destination, String type, Map<String, Object> relProps) {

        Relationship rel = source.createRelationshipTo(destination, RelationshipType.withName(type));
        relProps.forEach(rel::setProperty);
        return rel;
    }

    private Optional<Node> getRNode(Node entity) {
        return Optional.ofNullable(entity.getSingleRelationship(RelationshipType.withName("FOR"), Direction.INCOMING))
                .map(Relationship::getStartNode);
    }

    private Optional<Node> createNewSourceState(Node entitySource, LocalDateTime date) {

        Update updateProcedure = new UpdateBuilder().withLog(log).withDb(db).build().orElseThrow(() -> new VersionerCoreException("Unable to initialize update procedure"));
        return updateProcedure.patch(entitySource, Collections.emptyMap(), StringUtils.EMPTY, date)
                .map(n -> n.node)
                .findFirst();
    }
}
