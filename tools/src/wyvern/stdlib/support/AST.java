package wyvern.stdlib.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wyvern.stdlib.Globals;
import wyvern.target.corewyvernIL.Case;
import wyvern.target.corewyvernIL.FormalArg;
import wyvern.target.corewyvernIL.VarBinding;
import wyvern.target.corewyvernIL.decl.DefDeclaration;
import wyvern.target.corewyvernIL.decl.DelegateDeclaration;
import wyvern.target.corewyvernIL.decl.ModuleDeclaration;
import wyvern.target.corewyvernIL.decl.NamedDeclaration;
import wyvern.target.corewyvernIL.decl.TypeDeclaration;
import wyvern.target.corewyvernIL.decl.ValDeclaration;
import wyvern.target.corewyvernIL.decl.VarDeclaration;
import wyvern.target.corewyvernIL.decltype.AbstractTypeMember;
import wyvern.target.corewyvernIL.decltype.ConcreteTypeMember;
import wyvern.target.corewyvernIL.decltype.DeclType;
import wyvern.target.corewyvernIL.decltype.DefDeclType;
import wyvern.target.corewyvernIL.decltype.ValDeclType;
import wyvern.target.corewyvernIL.decltype.VarDeclType;
import wyvern.target.corewyvernIL.expression.Bind;
import wyvern.target.corewyvernIL.expression.BooleanLiteral;
import wyvern.target.corewyvernIL.expression.Cast;
import wyvern.target.corewyvernIL.expression.Expression;
import wyvern.target.corewyvernIL.expression.FFI;
import wyvern.target.corewyvernIL.expression.FFIImport;
import wyvern.target.corewyvernIL.expression.FieldGet;
import wyvern.target.corewyvernIL.expression.FieldSet;
import wyvern.target.corewyvernIL.expression.IExpr;
import wyvern.target.corewyvernIL.expression.IntegerLiteral;
import wyvern.target.corewyvernIL.expression.JavaValue;
import wyvern.target.corewyvernIL.expression.Let;
import wyvern.target.corewyvernIL.expression.Literal;
import wyvern.target.corewyvernIL.expression.Match;
import wyvern.target.corewyvernIL.expression.MethodCall;
import wyvern.target.corewyvernIL.expression.New;
import wyvern.target.corewyvernIL.expression.ObjectValue;
import wyvern.target.corewyvernIL.expression.StringLiteral;
import wyvern.target.corewyvernIL.expression.Value;
import wyvern.target.corewyvernIL.expression.Variable;
import wyvern.target.corewyvernIL.modules.TypedModuleSpec;
import wyvern.target.corewyvernIL.support.GenContext;
import wyvern.target.corewyvernIL.support.Util;
import wyvern.target.corewyvernIL.type.NominalType;
import wyvern.target.corewyvernIL.type.RefinementType;
import wyvern.target.corewyvernIL.type.StructuralType;
import wyvern.target.corewyvernIL.type.ValueType;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.parsing.coreparser.ParseException;
import wyvern.tools.parsing.coreparser.ParseUtils;
import wyvern.tools.parsing.coreparser.WyvernParser;
import wyvern.tools.tests.TestUtil;
import wyvern.tools.typedAST.core.declarations.ImportDeclaration;
import wyvern.tools.typedAST.interfaces.ExpressionAST;
import wyvern.tools.typedAST.interfaces.TypedAST;
import wyvern.tools.types.Type;
import wyvern.tools.util.Pair;

public class AST {
    public static final AST utils = new AST();

    private int identNum = 0;

    public ValueType intType() {
        return Util.intType();
    }

    public ValueType dynType() {
        return Util.dynType();
    }

    public ValueType unitType() {
        return Util.unitType();
    }

    public ValueType booleanType() {
        return Util.booleanType();
    }

    public ValueType stringType() {
        return Util.stringType();
    }

    public ValueType nominalType(String pathVariable, String typeMember) {
        return new NominalType(pathVariable, typeMember);
    }

    public ValueType structuralType(String selfName, List<ObjectValue> declTypeObjs) {
        List<DeclType> declTypes = new LinkedList<>();
        for (ObjectValue declType: declTypeObjs) {
            JavaValue fieldValue = (JavaValue) declType.getField("declType");
            throw new RuntimeException("implementation needs to be fixed");
            //return (ValueType) fieldValue.getWrappedValue();
        }
        return new StructuralType(selfName, declTypes);
    }

    public ValueType typeParam(String name, ObjectValue type) {
        List<DeclType> declTypes = new LinkedList<>();
        declTypes.add(concreteTypeMember(name, type));
        return new StructuralType("ignore$Me", declTypes);
    }

    public ValueType refinementType(List<ObjectValue> typeParamObjs, ObjectValue base) {
        List<ValueType> typeParams = new LinkedList<>();
        for (ObjectValue obj: typeParamObjs) {
            typeParams.add(getType(obj));
        }
        return new RefinementType(typeParams, getType(base), base);
    }

    public Expression intLiteral(int i) {
        return new IntegerLiteral(i);
    }

    public Value stringLiteral(String s) {
        return new StringLiteral(s);
    }

    public Value booleanLiteral(boolean b) {
        return new BooleanLiteral(b);
    }

    public Expression variable(String s) {
        return new Variable(s);
    }

    public Expression methodCall(ObjectValue receiver, String methodName, List<ObjectValue> arguments) {
        List<Expression> translArgs = new LinkedList<>();
        for (ObjectValue arg : arguments) {
            translArgs.add(getExpr(arg));
        }
        return new MethodCall(getExpr(receiver), methodName, translArgs, null);
    }

    public Expression object(List<ObjectValue> decls) {
        List<NamedDeclaration> javaDecls = new LinkedList<>();
        FileLocation loc = null;
        for (ObjectValue decl : decls) {
            JavaValue fieldValue = (JavaValue) decl.getField("decl");
            javaDecls.add((NamedDeclaration) fieldValue.getWrappedValue());
            loc = decl.getLocation();
        }
        return new New(javaDecls, loc);
    }

    private Expression getExpr(ObjectValue wyvernAST) {
        final Value ast = wyvernAST.getField("ast");
        if (ast instanceof JavaValue) {
            final JavaValue value = (JavaValue) ast;
            return (Expression) value.getWrappedValue();
        } else if (ast instanceof Literal) {
            return (Expression) ast;
        } else {
            throw new RuntimeException("unexpected!");
        }
    }

    private ValueType getType(ObjectValue wyvernType) {
        final JavaValue fieldValue = (JavaValue) wyvernType.getField("typ");
        return (ValueType) fieldValue.getWrappedValue();
    }

    private List<FormalArg> getFormalArgs(List<ObjectValue> objs) {
        List<FormalArg> formalArgs = new LinkedList<>();
        for (ObjectValue arg: objs) {
            final JavaValue fieldValue = (JavaValue) arg.getField("formalArg");
            formalArgs.add((FormalArg) fieldValue.getWrappedValue());
        }
        return formalArgs;
    }

    public DefDeclaration defDeclaration(String name, List<ObjectValue> formalArgObjs, ObjectValue returnType, ObjectValue body) {
        return new DefDeclaration(name, getFormalArgs(formalArgObjs), getType(returnType), getExpr(body), null);
    }

    public DelegateDeclaration delegateDeclaration(ObjectValue valueType, String fieldName) {
        return new DelegateDeclaration(getType(valueType), fieldName, null);
    }

    public ModuleDeclaration moduleDeclaration(String name, List<ObjectValue> formalArgObjs, ObjectValue returnType,
                                               ObjectValue body, List<String> dependencyURIs) throws URISyntaxException {
        List<Pair<ImportDeclaration, ValueType>> dependencies = new LinkedList<>();
        for (String dependency: dependencyURIs) {
            ImportDeclaration imp = new ImportDeclaration(new URI(dependency), null, null, false, false);
            Pair<VarBinding, GenContext> bindingCtx = imp.genBinding(Globals.getStandardGenContext(), new LinkedList<TypedModuleSpec>());
            dependencies.add(new Pair<ImportDeclaration, ValueType>(imp, bindingCtx.getFirst().getType()));
        }
        return new ModuleDeclaration(name, getFormalArgs(formalArgObjs), getType(returnType), getExpr(body), dependencies, null);
    }

    public TypeDeclaration typeDeclaration(String typeName, ObjectValue sourceType) {
        return new TypeDeclaration(typeName, getType(sourceType), null);
    }

    public ValDeclaration valDeclaration(String fieldName, ObjectValue fieldType, ObjectValue value) {
        return new ValDeclaration(fieldName, getType(fieldType), getExpr(value), null);
    }

    public VarDeclaration varDeclaration(String fieldName, ObjectValue fieldType, ObjectValue value) {
        return new VarDeclaration(fieldName, getType(fieldType), getExpr(value), null);
    }

    public IExpr let(String ident, ObjectValue identType, ObjectValue bindingValue, ObjectValue inExpr) {
        return new Let(ident, getType(identType), getExpr(bindingValue), getExpr(inExpr));
    }

    public VarBinding varBinding(String varName, ObjectValue bindingType, ObjectValue toReplace) {
        return new VarBinding(varName, getType(bindingType), getExpr(toReplace));
    }

    public IExpr bind(List<ObjectValue> bindingObjects, ObjectValue inExpr) {
        List<VarBinding> bindings = new LinkedList<>();
        for (ObjectValue bndObj: bindingObjects) {
            JavaValue fieldValue = (JavaValue) bndObj.getField("binding");
            bindings.add((VarBinding) fieldValue.getWrappedValue());
        }
        return new Bind(bindings, getExpr(inExpr));
    }

    public IExpr cast(ObjectValue toCastExpr, ObjectValue exprType) {
        return new Cast(getExpr(toCastExpr), getType(exprType));
    }

    public IExpr ffi(String importName, ObjectValue type) {
        return new FFI(importName, getType(type), null);
    }

    public IExpr ffiImport(ObjectValue ffiType, String path, ObjectValue importType) {
        return new FFIImport(getType(ffiType), path, getType(importType));
    }

    public IExpr fieldGet(ObjectValue objectExpr, String fieldName) {
        return new FieldGet(getExpr(objectExpr), fieldName, null);
    }

    public IExpr fieldSet(ObjectValue exprType, ObjectValue objectExpr, String fieldName, ObjectValue exprToAssign) {
        return new FieldSet(getType(exprType), getExpr(objectExpr), fieldName, getExpr(exprToAssign));
    }

    public IExpr matchExpr(ObjectValue matchObj, ObjectValue elseObj, List<ObjectValue> caseObjs) {
        List<Case> cases = new LinkedList<>();
        for (ObjectValue obj: caseObjs) {
            JavaValue fieldValue = (JavaValue) obj.getField("caseValue");
            cases.add((Case) fieldValue.getWrappedValue());
        }
        return new Match(getExpr(matchObj), getExpr(elseObj), cases);
    }

    public Case makeCase(String varName, ObjectValue pattern, ObjectValue body) {
        return new Case(varName, (NominalType) getType(pattern), getExpr(body));
    }

    public FormalArg formalArg(String name, ObjectValue type) {
        return new FormalArg(name, getType(type));
    }

    public DeclType abstractTypeMember(String name, boolean isResource) {
        return new AbstractTypeMember(name, isResource);
    }

    public DeclType concreteTypeMember(String name, ObjectValue sourceType) {
        return new ConcreteTypeMember(name, getType(sourceType));
    }

    public DeclType defDeclType(String methodName, ObjectValue returnType, List<ObjectValue> formalArgs) {
        return new DefDeclType(methodName, getType(returnType), getFormalArgs(formalArgs));
    }

    public DeclType valDeclType(String field, ObjectValue type) {
        return new ValDeclType(field, getType(type));
    }

    public DeclType varDeclType(String field, ObjectValue type) {
        return new VarDeclType(field, getType(type));
    }

    public IExpr parseExpression(String input, GenContext ctx) throws ParseException {
        try {
            ExpressionAST ast = (ExpressionAST) TestUtil.getNewAST(input.trim() + "\n", "TSL Parse");
            //GenContext ctx = (GenContext)context.getFObject().getWrappedValue();
            // Extend parseTSL with a second argument (abstract type representing context)
            // TODO: Handle InterpreterState/GenContext
            return ast.generateIL(ctx, null, new LinkedList<TypedModuleSpec>());
        } catch (ParseException e) {
            System.err.println("Error when running parseExpression on input \"" + input + "\"");
            throw e;
        }
    }

    public List<IExpr> parseExpressionList(String input, GenContext ctx) throws ParseException {
        List<IExpr> result = new LinkedList<>();
        Reader r = new StringReader(input);
        WyvernParser<TypedAST, Type> wp = ParseUtils.makeParser("parseExpressionList Parse", r);
        List<TypedAST> exprASTs = wp.ExpressionList();
        //GenContext ctx = (GenContext)context.getFObject().getWrappedValue();

        for (TypedAST ast: exprASTs) {
            result.add(((ExpressionAST) ast).generateIL(ctx, null, new LinkedList<TypedModuleSpec>()));
        }

        return result;
    }

    private String commonPrefix(String s1, String s2) {
        if (s1 == null) {
            return s2;
        }
        if (s2 == null) {
            return s1;
        }
        int minLen = Math.min(s1.length(), s2.length());
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < minLen; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                result.append(s1.charAt(i));
            } else {
                break;
            }
        }
        return result.toString();
    }

    public String stripLeadingWhitespace(String input) throws IOException {
        // Remove the least common whitespace prefix from all lines in [input]
        String toStrip = null;
        String line = null;
        BufferedReader bufReader = new BufferedReader(new StringReader(input));
        Pattern p = Pattern.compile("^(\\s+).*");
        while ((line = bufReader.readLine()) != null) {
            Matcher m = p.matcher(line);
            String leadingWhitespace;
            if (m.matches()) {
                leadingWhitespace = m.group(1);
            } else {
                leadingWhitespace = "";
            }
            toStrip = commonPrefix(leadingWhitespace, toStrip);
        }
        bufReader = new BufferedReader(new StringReader(input));
        StringBuilder result = new StringBuilder();
        while ((line = bufReader.readLine()) != null) {
            result.append(line.substring(toStrip.length()));
            result.append("\n");
        }
        return result.toString();
    }

    public String genIdent() {
        return "ASTIDENT$" + Integer.toString(++identNum);
    }

}
