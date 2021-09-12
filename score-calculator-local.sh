# set java to version 8
source ~/.bash_profile 
j8 
PROJECT="Math"
BUG_VER="95"
while [ "$1" != "" ];
do 
    case $1 in 
    -project ) PROJECT=$2 shift 
    ;;
    -bug ) BUG_VER=$2 shift 
    ;;
    esac
    shift 
done 
# CUT details 

# compile correct fix 
CORRECT_DIR=/Users/yoyo/Documents/Study/Research/local-AutoBoost/defects4j/fixed_versions/${PROJECT}_${BUG_VER}
cd $CORRECT_DIR
CORRECT_SRC_DIR=${CORRECT_DIR}
CORRECT_BIN_DIR=${CORRECT_DIR}/target/classes
if [ ! -d ${CORRECT_BIN_DIR} ]; then
    mvn clean package -DskipTests=true
fi
defects4j export -p tests.all -o ./all-tests.stdout
ORIGINAL_TEST_LIST=$(cat ./all-tests.stdout| tr '\n' ',')
defects4j export -p classes.modified -o ./classes-modified.stdout
BUGGY_CLASS=$(cat ./classes-modified.stdout| tr '\n' ',')

ASTOR_OUTPUT_DIR=/Users/yoyo/Documents/Study/Research/AutoBoost/resources/pre-AutoBoost/AstorMain-${PROJECT}_${BUG_VER}

# get list of plausible fixes 
cd ${ASTOR_OUTPUT_DIR}/src
if [ ! -f "${ASTOR_OUTPUT_DIR}/fix_list.txt" ]; then 
    PLAUSIBLE_LIST=""
    for FIX_ID in $(ls -d */ ); do 
        if [[ "$FIX_ID" != "default/" && $FIX_ID != *"_f"* ]]; then 
            PLAUSIBLE_LIST=${PLAUSIBLE_LIST}${FIX_ID%%/}\\n
        fi 
    done
    echo ${PLAUSIBLE_LIST} > ${ASTOR_OUTPUT_DIR}/fix_list.txt     
fi 

PLAUSIBLE_LIST=$(cat ${ASTOR_OUTPUT_DIR}/fix_list.txt) #no /, not parent dir and etc. 

# putting whole project, with replaced modified classes java in new directory 
# ensure all using same env and tools for compiling 

PLAUSIBLE_SRC_DIR=${ASTOR_OUTPUT_DIR}/plausible_fixes_src
mkdir ${PLAUSIBLE_SRC_DIR}
cd ${PLAUSIBLE_SRC_DIR}
# copy original src (unchanged) to plausible fix dir 
PLAUSIBLE_BIN_LIST=""
for FIX_ID in $PLAUSIBLE_LIST; do
    NEW_SRC=${PLAUSIBLE_SRC_DIR}/$FIX_ID
    for file in $(find $CORRECT_SRC_DIR -type f); do
        file=${file##*${CORRECT_SRC_DIR}/}
        if [[ $(dirname $file) != *"target"* ]]; 
        then
            if [ ! -f ${ASTOR_OUTPUT_DIR}/src/${FIX_ID}/${file##'src/java/'} ]; then 
            mkdir -p $(dirname ${NEW_SRC}/${file}) && cp -n -r ${CORRECT_SRC_DIR}/${file} ${NEW_SRC}/${file}
            else 
            mkdir -p $(dirname ${NEW_SRC}/${file}) && cp -n -r ${ASTOR_OUTPUT_DIR}/src/${FIX_ID}/${file##'src/java/'} ${NEW_SRC}/${file}
            fi
        fi
    done 

done
for FIX_ID in $PLAUSIBLE_LIST; do
    cd ${PLAUSIBLE_SRC_DIR}/$FIX_ID
    mvn clean package -DskipTests=true
    PLAUSIBLE_BIN_LIST=${PLAUSIBLE_BIN_LIST}${PLAUSIBLE_SRC_DIR}/$FIX_ID/target/classes:
    cd ..
done 
PLAUSIBLE_BIN_LIST=$(echo $PLAUSIBLE_BIN_LIST| sed 's/.$//')

RESULT_DIR=/Users/yoyo/Documents/Study/Research/AutoBoost/resources/AutoBoostResults/instrumentModifiedClass/${PROJECT}_${BUG_VER}
if [ ! -d ${RESULT_DIR} ]; then 
    mkdir -p ${RESULT_DIR}
fi
# test details 
TEST_BIN_DIR="/Users/yoyo/Documents/Study/Research/local-AutoBoost/defects4j/fixed_versions/Math_95/target/test-classes"
# build the tools, for development only 
cd /Users/yoyo/Documents/Study/Research/AutoBoost/JunitRunner
mvn clean package 
JUNIT_JAR=$(pwd)/$(find target -name "*-with-dependencies.jar" -type f)
cd /Users/yoyo/Documents/Study/Research/AutoBoost/AutoBoost
mvn clean package 
AUTOBOOST_JAR=$(pwd)/$(find target -name "*-with-dependencies.jar" -type f)
java -jar $AUTOBOOST_JAR -fixedPath ${CORRECT_BIN_DIR} -plausibleFixesPaths ${PLAUSIBLE_BIN_LIST%%:} -instrumentClasses ${BUGGY_CLASS} -testClassPaths ${TEST_BIN_DIR} -testClassNames ${ORIGINAL_TEST_LIST} -testRunnerJar ${JUNIT_JAR} -testRunnerClass application.JunitRunner -resultDir ${RESULT_DIR}