package ch.zhaw.splab.podilizerproc.awslambda;

import ch.zhaw.splab.podilizerproc.annotations.Lambda;
import com.sun.source.tree.*;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 *Entity of the AWS lambda function
 */
public class LambdaFunction {
    private MethodTree method;
    private ClassTree clazz;
    private CompilationUnitTree cu;
    private List<VariableTree> fields = new ArrayList<>();
    private Filer awsFiler;
    private Lambda lambdaAnnotation;

    public LambdaFunction(MethodTree method, ClassTree clazz, CompilationUnitTree cu, Lambda lambdaAnnotation) {
        this.method = method;
        this.clazz = clazz;
        this.cu = cu;
        this.lambdaAnnotation = lambdaAnnotation;
        for (Tree tree :
                clazz.getMembers()) {
            if (tree.getKind() == Tree.Kind.VARIABLE) {
                VariableTree field = (VariableTree)tree;
                //exclude static and final fields from list
                if (!field.getModifiers().getFlags().contains(Modifier.STATIC) &
                        !field.getModifiers().getFlags().contains(Modifier.FINAL)){
                    fields.add(field);
                }
            }
        }
        awsFiler = new Filer(cu, clazz, method);
    }

    public Lambda getLambdaAnnotation() {
        return lambdaAnnotation;
    }

    public Filer getAwsFiler() {
        return awsFiler;
    }

    /**
     * Generates java code lambda function for appropriate method
     * @return {@link String} of generated java code
     */
    public String create(){
        String result = importsToString(imports());
        result += "\n" + getClassSpecification();
        //result += "\n" + Utility.fieldsToString(fields);
        result += "\n" + generateHandler();
        result += "\n" + removeAnnotations(method);
        return result + "\n}";
    }

    /**
     * Creates InputType class java code
     * @return java code of InputType class as a {@link String}
     */
    public String createInputType(){
        InputTypeEntity inputType = new InputTypeEntity("InputType", method.getParameters());
        return inputType.create();
    }

    /**
     * Creates OutputType class java code
     * @return java code of OutputType class as a {@link String}
     */
    public String createOutputType(){
        OutputTypeEntity outputType = new OutputTypeEntity("OutputType", method.getReturnType());
        return outputType.create();
    }

    /**
     * Creates list of imports to be added to lambda function compilation unit
     * @return {@link String[]} of imports;
     */
    public List<String> imports(){
        List<String> imports = new ArrayList<>();
        String[] defaultImports = {
                "com.amazonaws.services.lambda.runtime.Context",
                "com.amazonaws.services.lambda.runtime.LambdaLogger",
                "java.io.*",
                "com.amazonaws.services.lambda.runtime.RequestStreamHandler",
                "com.amazonaws.util.IOUtils",
                "com.fasterxml.jackson.databind.*",
                "com.amazonaws.services.lambda.runtime.RequestHandler"
        };
//        for (ImportTree importTree :
//                cu.getImports()) {
//            imports.add(importTree.getQualifiedIdentifier().toString());
//        }
        for (String importStr :
                defaultImports) {
            imports.add(importStr);
        }
        return imports;
    }

    /**
     * Turns list of imports into string of java code
     * @param imports is list of imports(Strings)
     * @return {@link String} of combined imports ready ti insert as java code
     */
    private String importsToString(List<String> imports){
        StringBuilder result = new StringBuilder();
        for (String importStr :
                imports) {
            result.append("import " + importStr + ";\n");
        }
        return String.valueOf(result);
    }


    /**
     * Generates lambda function class specification. Based on 'implements' and 'extends' of external class
     * @return {@link String} of class declaration
     */
    private String getClassSpecification(){
        String result = "public class LambdaFunction";
        String implementsString = "implements RequestStreamHandler";
        for (Tree implement:
                clazz.getImplementsClause()) {
            implementsString += ", " + implement.toString();
        }
        if (clazz.getExtendsClause() == null){
            return result + " " + implementsString + " {";
        }
        String extendsString = "extends " + clazz.getExtendsClause().toString();

        return result + " " + extendsString + " " + implementsString + " {";
    }

    /**
     * Generates handler code
     * @return {@link String} of handler java code
     */
    private String generateHandler(){
        String result = "\tpublic void handleRequest(InputStream inputStream, OutputStream outputStream, " +
                "Context context) throws IOException {\n" +
                "\t\tlong time = System.currentTimeMillis();" +
                "\t\tObjectMapper objectMapper = new ObjectMapper();\n" +
                "\t\tobjectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);\n" +
                "\t\tString inputString = IOUtils.toString(inputStream);\n" +
                "\t\tInputType inputType = objectMapper.readValue(inputString, InputType.class);\n";
//        for (VariableTree field :
//                fields) {
//            String var = field.getName().toString();
//            result += "\t\tthis." + var + " = inputType.get" + Utility.firstLetterToUpperCase(var) + "();\n";
//        }
        result += "\t\t" + generateMethodCall()  + ";\n";
        result += "\t\tOutputType outputType = new OutputType(\"Lambda environment\"";
        result += ", System.currentTimeMillis() - time";
        if (!method.getReturnType().toString().equals("void")){
            result += ", result";
        }
        result += ");\n";
        result += "\t\tobjectMapper.writeValue(outputStream, outputType);\n";
        result += "\t}\n";
        return result;
    }


    /**
     * Removes "@Lambda" and "@Invoker" annotations from method and adds "\t" to every string
     * @param method to be formatted
     * @return {@link String} of formatted method
     */
    private String removeAnnotations(MethodTree method){
        String methodString = method.toString();
        String[] lines = methodString.split("\n");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++){
            if (!lines[i].startsWith("@Lambda") && !lines[i].startsWith("@Invoker")){
                result.append("\t");
                result.append(lines[i]);
                result.append("\n");
            }
        }
        return String.valueOf(result);
    }

    /**
     * Generates method call expression for lambda class
     * @return java code line as {@link String}
     */
    private String generateMethodCall(){
        String result = "";
        if (!method.getReturnType().toString().equals("void")){
            result +=  "" + method.getReturnType().toString() + " result = ";
        }
        result += method.getName().toString() + "(";
        int i = 0;
        for (VariableTree param :
                method.getParameters()){
            String getCall = "inputType.get" + Utility.firstLetterToUpperCase(param.getName().toString()) + "()";
            if (i == 0){
                result += getCall;
            } else {
                result += ", " + getCall;
            }
            i++;
        }
        result += ");\n";
        return result;
    }

    /**
     * Generates package declaration line for InputType
     * @return generated {@link String} with format 'aws.originalPackage.originalClass.originalMethod#argsNumber'
     */
    public String generateInputPackage(){
        String result = "package aws.";
        result += getLambdaFunctionName().replace('_', '.');
        return result + ";";
    }

    /**
     * Generates name for lambda function
     * @return {@link String} in format 'originalPackage_originalClass_originalMethod#argsNumber'
     */
    public String getLambdaFunctionName(){
        String result = "";
        if (cu.getPackageName() != null){
            result += cu.getPackageName().toString().replace('.', '_');
        }
        result += "_" + clazz.getSimpleName();
        result += "_" + method.getName().toString();
        if (method.getParameters() != null){
            result += method.getParameters().size();
        }
        return result;
    }

    public MethodTree getMethod() {
        return method;
    }

    @Override
    public String toString() {
        return "LambdaFunction{" +
                "method=" + method +
                ", clazz=" + clazz +
                ", cu=" + cu +
                '}';
    }
}
