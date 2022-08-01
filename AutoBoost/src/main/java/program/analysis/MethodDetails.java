package program.analysis;

import entity.ACCESS;
import entity.METHOD_TYPE;
import org.apache.commons.lang3.ClassUtils;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodDetails {
    private static final AtomicInteger methodIdGenerator = new AtomicInteger(0);
    private int id;
    private final List<Type> parameterTypes;
    private final int parameterCount;
    private final String name;
    private final Type returnSootType;
    private final ACCESS access;
    private final METHOD_TYPE type;
    private final SootClass declaringClass;
    private Class<?> dClass;
    private final String signature;
    private final String subSignature;

    public MethodDetails(SootMethod method) {
        this.id = methodIdGenerator.incrementAndGet();
        this.parameterTypes = method.getParameterTypes();
        this.parameterCount = method.getParameterCount();
        this.name = method.getName();
        this.signature = method.getSignature();
        if(method.isStaticInitializer())
            this.type = METHOD_TYPE.STATIC_INITIALIZER;
        else if(method.isStatic())
            this.type = METHOD_TYPE.STATIC;
        else if(method.isConstructor())
            this.type = METHOD_TYPE.CONSTRUCTOR;
        else this.type = METHOD_TYPE.MEMBER;
        this.returnSootType = method.getReturnType();
        this.access = method.isPrivate()? ACCESS.PRIVATE: (method.isPublic()? ACCESS.PUBLIC: ACCESS.PROTECTED);
        this.declaringClass = method.getDeclaringClass();
        this.subSignature = method.getSubSignature();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<Type> getParameterTypes() {
        return parameterTypes;
    }

    public String getName() {
        return name;
    }

    public ACCESS getAccess() {
        return access;
    }

    public METHOD_TYPE getType() {
        return type;
    }

    // may change to use ClassDetails later
    public SootClass getDeclaringClass() {
        return declaringClass;
    }

    public String getSignature() {
        return signature;
    }

    public int getParameterCount() {
        return parameterCount;
    }

    public Type getReturnSootType() {
        return returnSootType;
    }

    public Class<?> getdClass() {
        if(dClass == null) {
            try {
                dClass = ClassUtils.getClass(declaringClass.getName());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return dClass;
    }

    public void setdClass(Class<?> dClass) {
        this.dClass = dClass;
    }

    public String getSubSignature() {
        return subSignature;
    }

    @Override
    public String toString() {
        return "MethodDetails{" +
                "id=" + id +
                ", parameterTypes=" + parameterTypes +
                ", parameterCount=" + parameterCount +
                ", name='" + name + '\'' +
                ", access=" + access +
                ", type=" + type +
                ", declaringClass=" + declaringClass +
                ", signature='" + signature + '\'' +
                '}';
    }

}
