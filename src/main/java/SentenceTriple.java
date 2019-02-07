import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.trees.Tree;

public class SentenceTriple {

    private String subject;
    private String predicate;
    private String object;
    private String subAttrs;
    private String objAttrs;
    private Tree sentenceParseTree;

    private List<String> subjectModifier = new ArrayList<String>();
    private List<String> objectModifier = new ArrayList<String>();



    public String getSubject() {
        return subject;
    }
    public void setSubject(String subject) {
        this.subject = subject;
    }
    public String getPredicate() {
        return predicate;
    }
    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }
    public String getObject() {
        return object;
    }
    public void setObject(String object) {
        this.object = object;
    }
    public String getSubjAttrs() { return subAttrs; }
    public void setSubAttrs(String subAttrs) { this.subAttrs = subAttrs; }
    public String getObjAttrs() { return  objAttrs; }
    public void setObjAttrs(String objAttrs) { this.objAttrs = objAttrs; }



    public Tree getSentenceParseTree() {
        return sentenceParseTree;
    }
    public void setSentenceParseTree(Tree sentenceParseTree) {
        this.sentenceParseTree = sentenceParseTree;
    }
    public List<String> getSubjectModifier() {
        return subjectModifier;
    }
    public void setSubjectModifier(List<String> subjectModifier) {
        this.subjectModifier = subjectModifier;
    }
    public List<String> getObjectModifier() {
        return objectModifier;
    }
    public void setObjectModifier(List<String> objectModifier) {
        this.objectModifier = objectModifier;
    }
    @Override
    public String toString() {
        StringBuffer res = new StringBuffer();
        res.append("Sub: ").append(subject).append(" , Pred: ").append(predicate).append(" , Obj: ").append(object);
        res.append(" , SubAttr: ").append(subAttrs).append(" , ObjAttr: ").append(objAttrs);
        return res.toString();
    }

}