

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

public class SentenceTriplizer {


    private static Tree currentPredicate = null;
    private static Tree currentSubject = null;
    private static Tree currentAdj = null;

    private static Set<String> nounTagSet;
    private static Set<String> verbTagSet;
    private static Set<String> adjTagSet;
    private static Set<String> nounModifiersSet;

    private static StanfordCoreNLP pipeline ;

    static{

        nounTagSet = new HashSet<String>();
        nounTagSet.add("NN");
        nounTagSet.add("NNP");
        nounTagSet.add("NNPS");
        nounTagSet.add("NNS");
        nounTagSet.add("PRP");

        verbTagSet = new HashSet<String>();
        verbTagSet.add("VB");
        verbTagSet.add("VBD");
        verbTagSet.add("VBG");
        verbTagSet.add("VBN");
        verbTagSet.add("VBP");
        verbTagSet.add("VBZ");

        adjTagSet = new HashSet<String>();
        adjTagSet.add("JJ");
        adjTagSet.add("JJR");
        adjTagSet.add("JJS");

        nounModifiersSet = new HashSet<String>();
        nounModifiersSet.add("DT");
        nounModifiersSet.add("PRP$");
        nounModifiersSet.add("PRP");

        nounModifiersSet.add("POS");
        nounModifiersSet.add("JJ");
        nounModifiersSet.add("CD");
        nounModifiersSet.add("ADJP");
        nounModifiersSet.add("RB");
        nounModifiersSet.add("QP");
        nounModifiersSet.addAll(nounTagSet);

        init();
    }

    private static void init(){
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma, parse");
        pipeline= new StanfordCoreNLP(props, false);
    }

    public static Map<Integer,SentenceTriple> extractTriples(String fileName) throws IOException {

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

        int sentenceId = 0;

        Map<Integer,SentenceTriple> tripleList = new HashMap<Integer,SentenceTriple>();

        Annotation document = pipeline.process(text);


        for (CoreMap sentence : document.get(SentencesAnnotation.class)) {
            //System.out.println(sentence);
            SentenceTriple triple = extractTriplesForOneSentence(sentence);
            for(CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                if(word == triple.getPredicate()){
                    //System.out.println(word);
                    String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                    triple.setPredicate(lemma);
                }
            }
            tripleList.put(sentenceId, triple);
            sentenceId++;
        }


        return tripleList;
    }


    public static SentenceTriple extractTriplesForOneSentence(CoreMap sentence) {

        SentenceTriple triple = new SentenceTriple();
        Tree tree = sentence.get(TreeAnnotation.class);
        if(PRINT_TREE){
            tree.pennPrint();
        }
        Tree root = tree.firstChild();
        //System.out.println(root.label().value());
        if(root.label().value().equals("NP")){ //e.g. female name, null(female name, null)
            //e.g. name of person null(person, name)

            triple = extractTripleFromNPVP(root.firstChild().children());

            if(triple.getPredicate()==null){

                triple =  extractTriplesFromNPSBAR(root.firstChild().children());
            }
            if(triple.getPredicate() == null){
                triple = extractSubObjNoPred(tree, root);
            }


        }
        boolean containsSNP = false;
        boolean containsSVP = false;

        if(root.label().value().equals("S")||root.label().value().equals("SINV")||root.label().value().equals("UCP")  ){
            for(Tree child : root.children()) {
                //System.out.println(child.label().value());
                if(child.label().value().equals("NP")){
                    containsSNP = true;
                }
                if(child.label().value().equals("VP")){
                    containsSVP = true;
                }
            }
            if(containsSNP && containsSVP){

                triple = extractTripleFromNPVP(root.children());

            }
            else if(containsSNP && !containsSVP){
                //Get the NP child

                for(Tree child : root.children()) {

                    if(child.label().value().equals("NP")){

                        triple = extractTripleFromNPVP(child.children());

                        if(triple.getPredicate()==null){
                            triple = extractSubObjNoPred(tree, child);
                        }
                        break;
                    }
                }
            }


        }


        else if(root.label().value().equals("FRAG")){
            boolean containsNP= false;
            boolean containsSBAR= false;
            boolean containsPP= false;

            for(Tree child : root.children()) {
                if(child.label().value().equals("NP")){
                    containsNP = true;
                }
                if(child.label().value().equals("SBAR")){
                    containsSBAR = true;
                }

                if(child.label().value().equals("PP")){
                    containsPP = true;

                }
            }
            if(containsNP && containsSBAR){  //e.g. Jack who killed Mary killed(Jack, Mary)
                triple =  extractTriplesFromNPSBAR(root.children());
            }
            else if(containsNP && containsPP){//e.g. Company where Jack works  works(Jack,Company)
                triple =  extractTriplesFromFRAGNPPP(root.children());
            }
            else if(containsNP && !containsPP && !containsSBAR){//e.g. Company where Jack works  works(Jack,Company)

                //Check if the NP in turn contains SBAR and NP
                for(Tree child : root.firstChild().children()) {

                    if(child.label().value().equals("NP")){
                        containsNP = true;
                    }
                    if(child.label().value().equals("SBAR")){
                        containsSBAR = true;
                    }

                    if(child.label().value().equals("PP")){
                        containsPP = true;

                    }
                }
                if(containsNP && containsSBAR){  //e.g. Jack who killed Mary killed(Jack, Mary)
                    triple =  extractTriplesFromNPSBAR(root.firstChild().children());
                }
                else if(containsNP && containsPP){//e.g. Company where Jack works  works(Jack,Company)
                    triple =  extractTriplesFromFRAGNPPP(root.firstChild().children());
                }

                else if(containsNP && !containsPP && !containsSBAR){

                    triple = extractTripleFromNPVP(root.firstChild().children());

                    if(triple.getPredicate()==null){

                        triple =  extractTriplesFromNPSBAR(root.firstChild().children());


                    }
                    if(triple.getPredicate() == null){
                        triple = extractSubObjNoPred(tree, root);
                    }


                }
            }
        }


        return triple;


    }


    private static SentenceTriple extractTriplesFromFRAGNPPP(Tree[] children) {

        SentenceTriple triple = new SentenceTriple();

        for (Tree child: children) {

            if(child.label().value().equals("NP")){

                Tree subject = extractSubjectRest(child);

                if(subject!=null){

                    String attr =  extractAttributesForNouns(subject.siblings(child));

                    triple.setObject(prettyPrint(subject));

                    triple.setObjAttrs(attr);

                }
            }
            if(child.label().value().equals("PP")){

                for(Tree childOfChild : child.children()){

                    if(childOfChild.label().value().equals("SBAR")){

                        Tree[] sbarChildren = childOfChild.children();

                        for(Tree sbarChild : sbarChildren){

                            if(sbarChild.label().value().equals("S")){

                                SentenceTriple triple2 = extractTripleFromNPVP(sbarChild.children());
                                triple.setSubject(triple2.getSubject());
                                triple.setPredicate(triple2.getPredicate());
                            }

                        }
                    }
                }
            }
        }

        return triple;
    }

    private static SentenceTriple extractSubObjNoPred(Tree tree, Tree child) {

        SentenceTriple triple = new SentenceTriple();

        Tree sub = extractSubjectRest(child);

        if(sub!=null){

            String attr = extractAttributesForNouns(sub.siblings(child));

            triple.setSubject(prettyPrint(sub));

            triple.setSubAttrs(attr);
        }
        //if the sibling is an PP you can extract the noun in that PP as object
//			List<Tree> siblings = sub.siblings(tree);
        for(Tree sibling : child.children()){

            if(sibling.label().value().equals("PP")){
                Tree obj = extractSubjectRest(sibling);
                if(obj != null){
                    String attr = extractAttributesForNouns(obj.siblings(sibling));
                    triple.setObject(prettyPrint(obj));
                    triple.setObjAttrs(attr);

                }
            }
        }
        return triple;
    }

    private static SentenceTriple extractTriplesFromNPSBAR(Tree[] children) {

        SentenceTriple triple = new SentenceTriple();

        for (Tree child: children) {

            if(child.label().value().equals("NP")){

                Tree subject = extractSubjectRest(child);

                if(subject!=null){

                    String attr = extractAttributesForNouns(subject.siblings(child));

                    triple.setSubject(prettyPrint(subject));

                    triple.setSubAttrs(attr);

                }
            }

            if(child.label().value().equals("SBAR")){

                Tree[] sbarChildren = child.children();

                for(Tree sbarChild : sbarChildren){

                    if(sbarChild.label().value().equals("S")){

                        SentenceTriple triple2 = extractTripleFromNPVP(sbarChild.children());
                        if(triple2.getSubject() !=null){
                            triple.setObject(triple.getSubject());
                            triple.setSubject(triple2.getSubject());

                        }
                        else{
                            triple.setObject(triple2.getObject());

                        }

                        triple.setPredicate(triple2.getPredicate());
                    }

                }
            }

        }
        return triple;
    }

    private static SentenceTriple extractTripleFromNPVP(Tree[] children) {

        SentenceTriple triple = new SentenceTriple();

        Tree npSubtree;
        Tree vpSubtree;
        for (Tree child: children) {

            //The subject is extracted from NP
            if(child.label().value().equals("NP")){
                npSubtree = child;

                currentSubject=null; //reset

                Tree sub = extractSubjectRest(npSubtree);

                if(sub!=null){

                    String subStr = prettyPrint(sub);

                    String attr = "" ;

                    if(sub.siblings(npSubtree)!=null){
                        attr = extractAttributesForNouns(sub.siblings(npSubtree));
                    }
                    triple.setSubject(subStr);
                    triple.setSubAttrs(attr);
                }
            }
            //The predicate and object are extracted from VP
            else if(child.label().value().equals("VP")){

                vpSubtree = child;
                Tree pred = extractPredicateRest(vpSubtree);

                if(pred!=null){

                    String predStr = prettyPrint(pred);

                    triple.setPredicate(predStr);



                    Tree obj = extractObject(pred.siblings(vpSubtree));

                    if(obj!=null){
                        String objStr = prettyPrint(obj);

                        String attr ="";
                        if(obj.siblings(vpSubtree) != null){

                            attr = extractAttributesForNouns(obj.siblings(vpSubtree));
                        }

                        triple.setObject(objStr);
                        triple.setObjAttrs(attr);
                    }
                }
            }
        }
        return triple;
    }

    private static String prettyPrint(Tree pred) {
        return pred.firstChild().label().value();
    }


    public static Tree extractSubject(Tree npSubtree) {

        Tree[] iter = npSubtree.children();

        for(Tree ch : iter){

            if(nounTagSet.contains(ch.label().value())){

                currentSubject = ch;
                //System.out.println(currentSubject);
                return currentSubject;

            }
            else if (currentSubject == null){
                extractSubject(ch);
            }

        }
        return currentSubject;
    }

    public static Tree extractSubjectRest(Tree npSubtree) {
        currentSubject = null;
        return extractSubject(npSubtree);
    }



    private static Tree extractPredicate(Tree vpSubtree) {


        if(vpSubtree.isLeaf())
            return currentPredicate;
        List<Tree> subTrees = vpSubtree.getChildrenAsList();

        for(Tree ch : subTrees){

            if(verbTagSet.contains(ch.label().value())){
                currentPredicate = ch;
            }

            extractPredicate(ch);
        }
        return currentPredicate;

    }

    private static Tree extractPredicateRest(Tree vpSubtree) {
        currentPredicate = null;
        return extractPredicate(vpSubtree);

    }


    private static Tree extractObject(List<Tree> vpSiblings) {

        if(vpSiblings == null)
            return null;

        Tree object = null;
        for(Tree ch : vpSiblings){

            if(ch.label().value().equals("PP") || ch.label().value().equals("NP") ){
                //System.out.println(ch.label().value());
                object = extractSubjectRest(ch);
                if(object !=null)
                    return object;
            }
            else if(ch.label().value().equals("ADJP")){
                object = extractAdjective(ch);

                if(object !=null)

                    return object;
            }
        }
        return object;

    }


    public static Tree extractAdjective(Tree npSubtree) {

        Tree[] iter = npSubtree.children();

        for(Tree ch : iter){


            if(adjTagSet.contains(ch.label().value())){

                currentAdj = ch;

            }
            else{
                extractSubjectRest(ch);
            }

        }
        return currentAdj;
    }

    public static Tree extractAdjectiveRest(Tree npSubtree) {

        currentAdj = null;
        return extractAdjective(npSubtree);

    }



    public static String extractAttributesForNouns(List<Tree> npTree){

        if(npTree == null)
            return "";

        StringBuffer res = new StringBuffer();
        for(Tree e : npTree){

            if(nounModifiersSet.contains(e.label().value())){

                res.append(prettyPrint(e)).append(" ");
            }

        }

        return res.toString();
    }


    static boolean PRINT_TREE = false;


    public static void main(String[] args) throws IOException {

        String fileName = "/home/chandra/input.txt";

        Map<Integer, SentenceTriple> results = extractTriples(fileName);


        for(Entry<Integer, SentenceTriple> res: results.entrySet()){
            SentenceTriple triple = res.getValue();
            System.out.println(res.getKey());
//			System.out.println(triple.getSubject() + "\t" + triple.getPredicate() + "\t" + triple.getObject());
            //System.out.println( triple.getPredicate() + "(" + triple.getSubject() + "," +  triple.getObject() + ")");
            System.out.println(triple.toString());
            System.out.println("...");
        }
    }
}