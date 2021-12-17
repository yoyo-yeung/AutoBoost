package program.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import soot.SootClass;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public class ClassAnalysis {
    private static final Logger logger = LogManager.getLogger(ClassAnalysis.class);
    private volatile DefaultDirectedGraph<String, DefaultEdge> inheritance;

    private Set<String> interfaces = Collections.synchronizedSet(new HashSet<>());
    private Set<String> abstractClasses = Collections.synchronizedSet(new HashSet<>());
    private Set<String> concreteClasses = Collections.synchronizedSet(new HashSet<>());
    private Set<String> visitedSubClasses = Collections.synchronizedSet(new HashSet<>());
    public ClassAnalysis() {
        inheritance = new DefaultDirectedGraph<>(DefaultEdge.class);
    }

    public synchronized void addRelation(String superClass, String subClass) {
        if(inheritance == null)
            inheritance = new DefaultDirectedGraph<>(DefaultEdge.class);
        inheritance.addVertex(superClass);
        inheritance.addVertex(subClass);
        inheritance.addEdge(superClass, subClass);
    }

    public DefaultDirectedGraph<String, DefaultEdge> getInheritance() {
        return inheritance;
    }

    public Set<String> getInterfaces() {
        return interfaces;
    }

    public Set<String> getAbstractClasses() {
        return abstractClasses;
    }

    public Set<String> getConcreteClasses() {
        return concreteClasses;
    }

    public Set<String> getVisitedSubClasses() {
        return visitedSubClasses;
    }

    private void addInterface(String inter) {
        interfaces.add(inter);
    }

    private void addAbstractClass(String absClass) {
        abstractClasses.add(absClass);
    }

    private void addConcreteClass(String conClass) {
        concreteClasses.add(conClass);
    }

    public void addVisitedClass(String visited) {
        visitedSubClasses.add(visited);
    }

    public boolean isVisited(String c) {
        return visitedSubClasses.contains(c);
    }

    public void addClass(SootClass sootClass) {
        if(sootClass.isInterface())
            addInterface(sootClass.getName());
        if(sootClass.isAbstract())
            addAbstractClass(sootClass.getName());
        if(sootClass.isConcrete())
            addConcreteClass(sootClass.getName());
    }

    @Override
    public String toString() {
        return "ClassAnalysis{" +
                "inheritance=" + inheritance +
                ", interfaces=" + interfaces +
                ", abstractClasses=" + abstractClasses +
                ", concreteClasses=" + concreteClasses +
                ", visitedSubClasses=" + visitedSubClasses +
                '}';
    }
}
