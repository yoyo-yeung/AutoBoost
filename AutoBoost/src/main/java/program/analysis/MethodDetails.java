package program.analysis;

import entity.ACCESS;
import entity.METHOD_TYPE;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodDetails {
    private static final AtomicInteger methodIdGenerator = new AtomicInteger(0);
    private int id;
    private final SootMethod method;
    private final List<Type> parameterTypes;
    private final int parameterCount;
    private final String name;
    private final String returnType;
    private final ACCESS access;
    private final METHOD_TYPE type;
    private final SootClass declaringClass;
    private final String signature;

    public MethodDetails(SootMethod method) {
        this.id = methodIdGenerator.incrementAndGet();
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.parameterCount = method.getParameterCount();
        this.name = method.getName();
        this.returnType = method.getReturnType().toString();
        this.signature = method.getSignature();
        if(this.method.isStaticInitializer())
            this.type = METHOD_TYPE.STATIC_INITIALIZER;
        else if(this.method.isStatic())
            this.type = METHOD_TYPE.STATIC;
        else if(this.method.isConstructor())
            this.type = METHOD_TYPE.CONSTRUCTOR;
        else this.type = METHOD_TYPE.MEMBER;
        this.access = method.isPrivate()? ACCESS.PRIVATE: (method.isPublic()? ACCESS.PUBLIC: ACCESS.PROTECTED);
        this.declaringClass = method.getDeclaringClass();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public SootMethod getMethod() {
        return method;
    }

    public List<Type> getParameterTypes() {
        return parameterTypes;
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public ACCESS getAccess() {
        return access;
    }

    public METHOD_TYPE getType() {
        return type;
    }

    public SootClass getDeclaringClass() {
        return declaringClass;
    }

    public String getSignature() {
        return signature;
    }

    public int getParameterCount() {
        return parameterCount;
    }
}
