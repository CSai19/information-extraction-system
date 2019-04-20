import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

class Triplet<T, U, V> {

    private final T first;
    private final U second;
    private final V third;

    public Triplet(T first, U second, V third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public T getFirst() { return first; }
    public U getSecond() { return second; }
    public V getThird() { return third; }
}

public class BreakSentence {

    private static StanfordCoreNLP pipeline;

    private static ArrayList<String> subjTags = new ArrayList<>(Arrays.asList("nsubj" ,"nsubjpass", "xsubj"));
    private static ArrayList<String> dontCareTags = new ArrayList<>(Arrays.asList("conj_and", "conj:and", "conj",  "cc", "ref", "ccomp", "mwe", "acl:relcl", "punct"));

    static {
        init();
    }

    private static void init() {
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, parse, lemma");
        pipeline = new StanfordCoreNLP(props);
    }

    public static String breakSentence(SemanticGraph graph){
        SortedSet<Pair<Integer, String> > edgeSet = new TreeSet<>();
        List<List <Triplet<Integer, String, String>> > adjList = new ArrayList<>(1000);
        for(int i=0;i<1000;i++)
            adjList.add(new ArrayList<>());

        int noofSub = 0;

        for (SemanticGraphEdge e : graph.edgeIterable()) {
            if(!dontCareTags.contains(e.getRelation().toString())) {
                adjList.get(e.getGovernor().index()).add(new Triplet<>(e.getDependent().index(), e.getDependent().word(), e.getRelation().toString()));
                adjList.get(e.getDependent().index()).add(new Triplet<>(e.getGovernor().index(), e.getGovernor().word(), e.getRelation().toString()));
            }
            if(subjTags.contains(e.getRelation().toString()))
                noofSub++;
        }

        String ret = "";

        for (SemanticGraphEdge e : graph.edgeIterable()) {
            if(subjTags.contains(e.getRelation().toString())){
                int visited[] = new int[1000];
                Queue<Integer> q = new LinkedList<>();
                SortedSet<Pair<Integer, String> > sen = new TreeSet<>();
                q.add(e.getGovernor().index());
                q.add(e.getDependent().index());
                visited[e.getGovernor().index()] = 1;
                visited[e.getDependent().index()] = 1;
                sen.add(Pair.makePair(e.getDependent().index(), e.getDependent().word()));
                sen.add(Pair.makePair(e.getGovernor().index(), e.getGovernor().word()));

                while (!q.isEmpty()){
                    int r = q.remove();
                    for(int i=0; i<adjList.get(r).size();i++){
                        if(visited[adjList.get(r).get(i).getFirst()]==0 && !subjTags.contains(adjList.get(r).get(i).getThird())){
                            q.add(adjList.get(r).get(i).getFirst());
                            visited[adjList.get(r).get(i).getFirst()] = 1;
                            sen.add(Pair.makePair(adjList.get(r).get(i).getFirst(), adjList.get(r).get(i).getSecond()));
                        }
                    }
                }

                for(Pair<Integer, String> p: sen){
                    ret += p.second();
                    ret += " ";
                }
                ret += "\n";
                sen.clear();

            }
        }
        return ret;

    }

    public static void main(String[] args) throws IOException {

        String fileName = "/home/chandra/input3.txt";

        File file = new File(fileName);
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String text = "", line;
        while ((line = br.readLine()) != null) {
            //process the line
            text = text + line;
        }
        //close resources
        br.close();
        fr.close();

        List<SemanticGraph> dependencies = new ArrayList<SemanticGraph>();

        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

        for (CoreMap sentence : sentences) {
            dependencies.add(sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));
        }

        if (dependencies.size() == 0)
            System.err.println("ERROR: Could not find dependencies");
        else {

            for (SemanticGraph graph : dependencies) {
                for (SemanticGraphEdge e : graph.edgeIterable()) {
                    System.out.printf("%s(%s-%d, %s-%d)%n", e.getRelation().toString(), e.getGovernor().word(), e.getGovernor().index(), e.getDependent().word(), e.getDependent().index());
                }
                String ret = breakSentence(graph);
                System.out.println(ret);
                System.out.println("------------");
            }
        }
    }

}
