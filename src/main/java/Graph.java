import org.neo4j.driver.v1.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.neo4j.driver.v1.Values.parameters;

public class Graph
{
    // Driver objects are thread-safe and are typically made available application-wide.
    Driver driver;

    public Graph(String uri, String user, String password)
    {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    private void addNode(String name)
    {
        // Sessions are lightweight and disposable connection wrappers.
        try (Session session = driver.session())
        {
            // Wrapping Cypher in an explicit transaction provides atomicity
            // and makes handling errors much easier.
            try (Transaction tx = session.beginTransaction())
            {
                tx.run("MERGE (a:Node {value: {x}})", parameters("x", name));
                tx.success();  // Mark this write as successful.
            }
        }
    }

    private void addRelationship(String node1, String node2, String relation)
    {
        // Sessions are lightweight and disposable connection wrappers.
        try (Session session = driver.session())
        {
            // Wrapping Cypher in an explicit transaction provides atomicity
            // and makes handling errors much easier.
            try (Transaction tx = session.beginTransaction())
            {
                    tx.run("MATCH (j:Node {value: {x}})\n" +
                            "MATCH (k:Node {value: {y}})\n" +
                            "MERGE (j)-[r:" + relation + "]->(k)", parameters("x", node1, "y", node2));
                    tx.success();  // Mark this write as successful.
            }
        }
    }

    public void close()
    {
        // Closing a driver immediately shuts down all open connections.
        driver.close();
    }

    public static void main(String... args) throws IOException
    {
        Graph graph = new Graph("bolt://localhost:7687", "neo4j", "reddy");

        graph.addNode("No Object");
        
        String fileName = "/home/chandra/input3.txt";

        Map<Integer, SentenceTriple> results = SentenceTriplizer.extractTriples(fileName);


        for(Map.Entry<Integer, SentenceTriple> res: results.entrySet()){
            SentenceTriple triple = res.getValue();

            graph.addNode(triple.getSubject().toLowerCase());
            if(triple.getObject()!=null)
                graph.addNode(triple.getObject().toLowerCase());
            if(triple.getPredicate()!=null) {
                if(triple.getObject()!=null)
                    graph.addRelationship(triple.getSubject().toLowerCase(),triple.getObject().toLowerCase(),triple.getPredicate().toLowerCase());
                else
                    graph.addRelationship(triple.getSubject().toLowerCase(),"No Object",triple.getPredicate().toLowerCase());
            }
        }
        graph.close();

    }
}