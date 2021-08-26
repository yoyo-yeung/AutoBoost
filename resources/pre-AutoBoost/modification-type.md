# Details of stored bugs 

This markdown aims to store details on the ator-executed project bugs, such that the package and class modified can be compared for better evaluation. 


## Project : Math 
|bug id | modified class | Plausible fixes generated?| seed| 
|--------|----------------|--------------- |---| 
| 85| org.apache.commons.math.analysis.solvers.UnivariateRealSolverUtils |Y| 10|
|94|org.apache.commons.math.util.MathUtils|N|10|
| 95| org.apache.commons.math.distribution.FDistributionImpl | Y|10|

> Reason for using different seed: 
> The project is claimed to be fixed in the paper, so multiple seed is tested to retrieve at least one plausible fixes 

#### Commands used 
```
PROJECT_LOC = ... # location of the project under repair 
seed = ... # specified in the table above

cd $(PROJECT_LOC)
defects4j compile
mvn dependency:copy-dependencies
nohup defects4j info -p ${PROJECT} -b ${BUG_VER} > info.stdout

DEPENDENCIES_FILES=$(find ${PROJECT_LOC}/target/dependency -name '*.jar' | tr '\n' ':' | sed 's/.$//')
FAILING_TEST=`grep -m 1 'org.apache.commons.*Test' ${PROJECT_LOC}/info.stdout | cut -f 2 -d '-' | cut -f 1 -d ':'`
java -cp astor.jar fr.inria.main.evolution.AstorMain -mode jgenprog -maxgen 9999999 -population 10 -stopfirst false -location $PROJECT_LOC -package $PACKAGE -failing $FAILING_TEST -dependencies $DEPENDENCIES_FILES -tmax1 9999999 -tmax2 9999999 -parameters overridemaxtime:false:maxtime:1800:processoutputinfile:true:outputjsonresult:true:timezone:Asia/Hong_Kong:logtestexecution:true:loglevel:DEBUG:regressionforfaultlocalization:false -seed ${seed} -scope package
```