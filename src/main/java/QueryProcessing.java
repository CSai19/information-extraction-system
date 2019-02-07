import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import org.neo4j.driver.v1.*;

import static org.neo4j.driver.v1.Values.parameters;


public class QueryProcessing {


    private static StanfordCoreNLP pipeline;

    private static Set<String> whSet;
    private static Set<String> auxVerbSet;

    private static ArrayList<String> subjTags = new ArrayList<>(Arrays.asList("nsubj" ,"compound", "nmod:poss"));
    private static ArrayList<String> objTags = new ArrayList<>(Arrays.asList("dobj", "nobj", "nmod:of", "nmod", "nmod:poss", "nsubjpass", "dep"));
    private static HashSet<String> twoWayReln = new HashSet<>(Arrays.asList("spouse", "brother", "sister", "know", "friend", "cousin", "colleague", "classmate", "marry"));

    Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "reddy"));

    static {
        whSet = new HashSet<String>();
        whSet.add("who");
        whSet.add("whom");
        whSet.add("whose");
        whSet.add("why");
        whSet.add("what");
        whSet.add("when");
        whSet.add("where");
        whSet.add("which");
        whSet.add("how");


        auxVerbSet = new HashSet<String>();
        auxVerbSet.add("do");
        auxVerbSet.add("does");
        auxVerbSet.add("did");
        auxVerbSet.add("is");
        auxVerbSet.add("am");
        auxVerbSet.add("are");
        auxVerbSet.add("was");
        auxVerbSet.add("were");
        auxVerbSet.add("has");
        auxVerbSet.add("have");
        auxVerbSet.add("had");

        init();
    }

    private static void init() {
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, parse, lemma");
        pipeline = new StanfordCoreNLP(props);
    }

    public static SentenceTriple processNode(IndexedWord root, String grammaticalRelation, List<Pair<GrammaticalRelation, IndexedWord>> children){
        SentenceTriple triple = new SentenceTriple();
        if(subjTags.contains(grammaticalRelation)&&!whSet.contains(root.value().toLowerCase())&&!auxVerbSet.contains(root.value().toLowerCase())){
            triple.setSubject(root.value());
        }
        else if(objTags.contains(grammaticalRelation)&&!whSet.contains(root.value().toLowerCase())&&!auxVerbSet.contains(root.value().toLowerCase())){
            triple.setObject(root.value());
        }
        else {
            //System.out.println(root.value());
            triple.setPredicate(root.value());
        }
        for(Pair<GrammaticalRelation, IndexedWord> p : children){
            if(subjTags.contains(p.first().getShortName())&&!whSet.contains(p.second().value().toLowerCase())&&!auxVerbSet.contains(p.second().value().toLowerCase())){
                triple.setSubject(p.second().value());
            }
            else if(objTags.contains(p.first().getShortName())&&!whSet.contains(p.second().value().toLowerCase())&&!auxVerbSet.contains(p.second().value().toLowerCase())){
                triple.setObject(p.second().value());
            }
        }
        return triple;
    }

    public static List<SentenceTriple> processQuery(SemanticGraph graph){
        Collection<IndexedWord> roots = graph.getRoots();
        ArrayList<SentenceTriple> result = new ArrayList<SentenceTriple>();
        for(IndexedWord r : roots){
            Queue<Pair<GrammaticalRelation, IndexedWord>> q = new LinkedList<>();
            //System.out.println(r.value());
            if((!(whSet.contains(r.value().toLowerCase()))&&(!auxVerbSet.contains(r.value().toLowerCase()))) || r.tag().equals("VB")){
                SentenceTriple triple = processNode(r, "root", graph.childPairs(r));
                result.add(triple);
            }
            ((LinkedList<Pair<GrammaticalRelation, IndexedWord>>) q).addAll(graph.childPairs(r));
            while (!q.isEmpty()){
                Pair<GrammaticalRelation, IndexedWord> p = ((LinkedList<Pair<GrammaticalRelation, IndexedWord>>) q).removeFirst();
                //System.out.println(p.first().getShortName());
                ((LinkedList<Pair<GrammaticalRelation, IndexedWord>>) q).addAll(graph.childPairs(p.second()));
                if((subjTags.contains(p.first().getShortName())|| objTags.contains(p.first().getShortName())) && (result.size()==0)){
                    if ((!(whSet.contains(p.second().value().toLowerCase())) && (!auxVerbSet.contains(p.second().value().toLowerCase()))) || p.second.tag().equals("VB")) {
                        SentenceTriple triple = processNode(p.second(), p.first().getShortName(), graph.childPairs(p.second()));
                        result.add(triple);
                    }
                }
            }

        }
        return result;
    }

    public int determineQueryType(SentenceTriple triple){
        String sub,pred,obj;
        sub = triple.getSubject(); pred = triple.getPredicate(); obj = triple.getObject();

        if(sub!=null&& pred!=null&&obj!=null)
            return 0;
        else if(sub!=null&&pred!=null&&obj==null)
            return 1;
        else if(sub!=null&&pred==null&&obj!=null)
            return 2;
        else if(sub!=null&&pred==null&&obj==null)
            return 3;
        else if(sub==null&&pred!=null&&obj!=null)
            return 4;
        else if(sub==null&&pred!=null&&obj==null)
            return 5;
        else
            return 6;
    }

    public void extractAnswer(SentenceTriple triple){
        int queryType = determineQueryType(triple);

        if(queryType==0){
            try (Session session = driver.session())
            {
                // Wrapping Cypher in an explicit transaction provides atomicity
                // and makes handling errors much easier.
                try (Transaction tx = session.beginTransaction())
                {
                    StatementResult result;
                    if(twoWayReln.contains(triple.getPredicate().toLowerCase())) {
                        result = tx.run("MATCH (j:Node {value: {x}})" + " -[r:" + triple.getPredicate().toLowerCase() + "]-" + " (k:Node {value: {y}})"
                                        + "RETURN j,r,k"
                                , parameters("x", triple.getSubject().toLowerCase(), "y", triple.getObject().toLowerCase()));
                    }
                    else {
                        result = tx.run("MATCH (j:Node {value: {x}})" + " -[r:" + triple.getPredicate().toLowerCase() + "]->" + " (k:Node {value: {y}})"
                                        + "RETURN j,r,k"
                                , parameters("x", triple.getSubject().toLowerCase(), "y", triple.getObject().toLowerCase()));
                    }
                    tx.success();  // Mark this write as successful.
                    if(result.hasNext())
                        System.out.println("Yes");
                    else
                        System.out.println("No");
                }
            }
            return;
        }
        else if(queryType==1){
            try (Session session = driver.session())
            {
                try (Transaction tx = session.beginTransaction())
                {
                    StatementResult result;
                    if(twoWayReln.contains(triple.getPredicate().toLowerCase())) {
                        result = tx.run("MATCH (j:Node {value: {x}})" + " -[r:" + triple.getPredicate().toLowerCase() + "]-" + " (k:Node)"
                                        + "RETURN k.value"
                                , parameters("x", triple.getSubject().toLowerCase()));
                    }
                    else{
                        result = tx.run("MATCH (j:Node {value: {x}})" + " -[r:" + triple.getPredicate().toLowerCase() + "]->" + " (k:Node)"
                                        + "RETURN k.value"
                                , parameters("x", triple.getSubject().toLowerCase()));
                    }
                    tx.success();  // Mark this write as successful.
                    while (result.hasNext())
                    {
                        Record record = result.next();
                        // Values can be extracted from a record by index or name.
                        System.out.println(record.get("k.value").asString());
                    }
                }
            }
            return;
        }
        else if(queryType==2){
            try (Session session = driver.session())
            {
                try (Transaction tx = session.beginTransaction())
                {
                    StatementResult result;
                    if(twoWayReln.contains(triple.getPredicate().toLowerCase())) {
                       result = tx.run("MATCH (j:Node {value: {x}})" + " -[r]-" + " (k:Node {value: {y}})"
                                        + "RETURN type(r)"
                                , parameters("x", triple.getSubject().toLowerCase(), "y", triple.getObject().toLowerCase()));
                    }
                    else{
                        result = tx.run("MATCH (j:Node {value: {x}})" + " -[r]->" + " (k:Node {value: {y}})"
                                        + "RETURN type(r)"
                                , parameters("x", triple.getSubject().toLowerCase(), "y", triple.getObject().toLowerCase()));
                    }
                    tx.success();  // Mark this write as successful.
                    while (result.hasNext())
                    {
                        Record record = result.next();
                        // Values can be extracted from a record by index or name.
                        System.out.println(record.get("type(r)").asString());
                    }
                }
            }
            return;
        }
        else if(queryType==3){
            try (Session session = driver.session())
            {
                try (Transaction tx = session.beginTransaction())
                {
                    StatementResult result;
                    if(twoWayReln.contains(triple.getPredicate().toLowerCase())) {
                        result = tx.run("MATCH (j:Node {value: {x}})" + " -[r]-" + " (k:Node)"
                                        + "RETURN k.value,type(r)"
                                , parameters("x", triple.getSubject().toLowerCase()));
                    }
                    else{
                        result = tx.run("MATCH (j:Node {value: {x}})" + " -[r]->" + " (k:Node)"
                                        + "RETURN k.value,type(r)"
                                , parameters("x", triple.getSubject().toLowerCase()));
                    }
                    tx.success();  // Mark this write as successful.
                    while (result.hasNext())
                    {
                        Record record = result.next();
                        // Values can be extracted from a record by index or name.
                        System.out.println(record.get("k.value").asString() + "--" +record.get("type(r").asString()) ;
                    }
                }
            }
            return;
        }
        else if(queryType==4){
            try (Session session = driver.session())
            {
                try (Transaction tx = session.beginTransaction())
                {
                    StatementResult result;
                    if(twoWayReln.contains(triple.getPredicate().toLowerCase())) {
                       result = tx.run("MATCH (j:Node)" + " -[r:" + triple.getPredicate().toLowerCase() + "]-" + " (k:Node {value: {y}})"
                                        + "RETURN j.value"
                                , parameters("y", triple.getObject().toLowerCase()));
                    }
                    else{
                        result = tx.run("MATCH (j:Node)" + " -[r:" + triple.getPredicate().toLowerCase() + "]->" + " (k:Node {value: {y}})"
                                        + "RETURN j.value"
                                , parameters("y", triple.getObject().toLowerCase()));
                    }
                    tx.success();  // Mark this write as successful.
                    while (result.hasNext())
                    {
                        Record record = result.next();
                        // Values can be extracted from a record by index or name.
                        System.out.println(record.get("j.value").asString());
                    }
                }
            }
            return;
        }
        else if(queryType==5){
            try (Session session = driver.session())
            {
                try (Transaction tx = session.beginTransaction())
                {
                    StatementResult result;
                    if(twoWayReln.contains(triple.getPredicate().toLowerCase())) {
                        result = tx.run("MATCH (j:Node)" + " -[r:" + triple.getPredicate().toLowerCase() + "]-" + " (k:Node)"
                                        + "RETURN j.value,k.value"
                                , parameters("y", triple.getObject().toLowerCase()));
                    }
                    else{
                        result = tx.run("MATCH (j:Node)" + " -[r:" + triple.getPredicate().toLowerCase() + "]->" + " (k:Node)"
                                        + "RETURN j.value,k.value"
                                , parameters("y", triple.getObject().toLowerCase()));
                    }
                    tx.success();  // Mark this write as successful.
                    while (result.hasNext())
                    {
                        Record record = result.next();
                        // Values can be extracted from a record by index or name.
                        System.out.println(record.get("j.value").asString() + "--" + record.get("k.value").asString());
                    }
                }
            }
            return;
        }
        else{
            try (Session session = driver.session())
            {
                try (Transaction tx = session.beginTransaction())
                {
                    StatementResult result;
                    if(twoWayReln.contains(triple.getPredicate().toLowerCase())) {
                         result = tx.run("MATCH (j:Node)" + " -[r]-" + " (k:Node {value: {y}})"
                                        + "RETURN j.value,type(r)"
                                , parameters("y", triple.getObject().toLowerCase()));
                    }
                    else{
                        result = tx.run("MATCH (j:Node)" + " -[r]->" + " (k:Node {value: {y}})"
                                        + "RETURN j.value,type(r)"
                                , parameters("y", triple.getObject().toLowerCase()));
                    }
                    tx.success();  // Mark this write as successful.
                    while (result.hasNext())
                    {
                        Record record = result.next();
                        // Values can be extracted from a record by index or name.
                        System.out.println(record.get("j.value").asString() + "--" + record.get("type(r)").asString());
                    }
                }
            }
            return;
        }
    }

    public void close()
    {
        // Closing a driver immediately shuts down all open connections.
        driver.close();
    }

    public static void main(String[] args) throws IOException{

        String fileName = "/home/chandra/query.txt";
        QueryProcessing queryProcessing = new QueryProcessing();

        File file = new File(fileName);
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String text = "",line;
        while((line = br.readLine()) != null){
            //process the line
            text = text + line;
        }
        //close resources
        br.close();
        fr.close();

        List<SemanticGraph> dependencies = new  ArrayList<SemanticGraph>();

        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);

        for(CoreMap sentence : sentences) {
                dependencies.add(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class));

        }

        if(dependencies.size() == 0)
            System.err.println("ERROR: Could not find dependencies");
        else {

            for(SemanticGraph graph : dependencies) {

                System.out.println("Graph:");
                System.out.println(graph.toString());

                Collection<IndexedWord> roots = graph.getRoots();

                for(IndexedWord r : roots) {
                    System.out.println("Root:");
                    System.out.println(r.toString() );
                    System.out.println("Child Pairs:");
                    System.out.println(graph.childPairs(r).toString() );
                }

                System.out.println();

            }
        }

        for(CoreMap sentence : sentences){
            SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
            List<SentenceTriple> result = processQuery(graph);
            for(SentenceTriple triple : result){
                for(CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                    String word = token.get(CoreAnnotations.TextAnnotation.class);
                    if(word == triple.getPredicate()){
                        //System.out.println(word);
                        String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                        triple.setPredicate(lemma);
                    }
                }
                System.out.println(triple.getSubject() + "--" + triple.getPredicate() + "--" + triple.getObject());
                queryProcessing.extractAnswer(triple);
            }
            System.out.println("---------------");
        }

        queryProcessing.close();
    }
}