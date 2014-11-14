package ro.redeul.google.go.lang.psi.impl.expressions.literals;

import com.intellij.lang.ASTNode;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import ro.redeul.google.go.lang.packages.GoPackages;
import ro.redeul.google.go.lang.parser.GoElementTypes;
import ro.redeul.google.go.lang.psi.GoPackage;
import ro.redeul.google.go.lang.psi.expressions.literals.GoLiteralIdentifier;
import ro.redeul.google.go.lang.psi.expressions.literals.composite.GoLiteralCompositeValue;
import ro.redeul.google.go.lang.psi.expressions.primary.GoSelectorExpression;
import ro.redeul.google.go.lang.psi.impl.GoPsiElementBase;
import ro.redeul.google.go.lang.psi.impl.expressions.primary.GoLiteralExpressionImpl;
import ro.redeul.google.go.lang.psi.patterns.GoElementPatterns;
import ro.redeul.google.go.lang.psi.resolve.Reference;
import ro.redeul.google.go.lang.psi.resolve.refs.FunctionOrTypeNameReference;
import ro.redeul.google.go.lang.psi.resolve.refs.InterfaceMethodReference;
import ro.redeul.google.go.lang.psi.resolve.refs.MethodReference;
import ro.redeul.google.go.lang.psi.resolve.refs.PackageReference;
import ro.redeul.google.go.lang.psi.resolve.refs.PackageSymbolReference;
import ro.redeul.google.go.lang.psi.resolve.refs.ShortVarReference;
import ro.redeul.google.go.lang.psi.resolve.refs.StructFieldReference;
import ro.redeul.google.go.lang.psi.resolve.refs.TypedConstReference;
import ro.redeul.google.go.lang.psi.resolve.refs.VarOrConstReference;
import ro.redeul.google.go.lang.psi.types.GoPsiTypeName;
import ro.redeul.google.go.lang.psi.typing.GoType;
import ro.redeul.google.go.lang.psi.typing.GoTypeMap;
import ro.redeul.google.go.lang.psi.typing.GoTypeName;
import ro.redeul.google.go.lang.psi.typing.GoTypePackage;
import ro.redeul.google.go.lang.psi.typing.GoTypePointer;
import ro.redeul.google.go.lang.psi.typing.GoTypePrimitive;
import ro.redeul.google.go.lang.psi.typing.GoTypeStruct;
import ro.redeul.google.go.lang.psi.typing.GoTypes;
import ro.redeul.google.go.lang.psi.utils.GoPsiUtils;
import ro.redeul.google.go.lang.psi.visitors.GoElementVisitor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static ro.redeul.google.go.lang.psi.utils.GoPsiUtils.getGlobalElementSearchScope;
import static ro.redeul.google.go.lang.psi.utils.GoPsiUtils.getLocalElementSearchScope;

/**
 * Author: Toader Mihai Claudiu <mtoader@gmail.com>
 * <p/>
 * Date: Jul 24, 2010
 * Time: 10:43:49 PM
 */
public class GoLiteralIdentifierImpl extends GoPsiElementBase implements GoLiteralIdentifier {

    public static final PsiElementPattern.Capture<PsiElement> INSIDE_COMPOSITE_KEY =
            psiElement()
                    .withParent(
                            psiElement(GoLiteralExpressionImpl.class)
                                    .withParent(psiElement(GoElementTypes.LITERAL_COMPOSITE_ELEMENT_KEY))
                    );

    public static final PsiElementPattern.Capture<PsiElement> SELECTOR_MATCHER =
            psiElement()
                    .withParent(psiElement(GoSelectorExpression.class));

    private final boolean isIota;
    private BigInteger iotaValue;

    public GoLiteralIdentifierImpl(@NotNull ASTNode node) {
        this(node, false);
    }

    public GoLiteralIdentifierImpl(@NotNull ASTNode node, boolean isIota) {
        super(node);
        this.isIota = isIota;
    }

    @Override
    public boolean isBlank() {
        return getText().equals("_");
    }

    @Override
    public boolean isIota() { return isIota; }

    @Override
    public boolean isNil() { return getText().equals("nil"); }

    @NotNull
    @Override
    public String getValue() {
        return getText();
    }

    @Override
    public Type getType() {
        return Type.Identifier;
    }

    public void accept(GoElementVisitor visitor) {
        visitor.visitLiteralIdentifier(this);
    }

    @Override
    @NotNull
    public String getName() {
        return getText();
    }

    @Override
    public PsiElement setName(@NonNls @NotNull String name)
            throws IncorrectOperationException {
        return null;
    }

    @NotNull
    @Override
    public PsiReference[] defineReferences() {

//        if (LabelReference.MATCHER.accepts(this))
//            return refs(new LabelReference(this));

//        if (ShortVarDeclarationReference.MATCHER.accepts(this))
//            return refs(new ShortVarDeclarationReference(this));

        if (INSIDE_COMPOSITE_KEY.accepts(this)) {
            GoLiteralCompositeValue compositeValue = GoPsiUtils.findParentOfType(this, GoLiteralCompositeValue.class);

            if (compositeValue == null)
                return PsiReference.EMPTY_ARRAY;

            final GoLiteralIdentifier identifier = this;

            compositeValue.getType();

            GoType enclosingType = compositeValue.getType();
            List<Reference> references = enclosingType.getUnderlyingType().accept(new GoType.ForwardingVisitor<List<Reference>>(
                    new ArrayList<Reference>(),
                    new GoType.Second<List<Reference>>() {
                        @Override
                        public void visitStruct(GoTypeStruct type, List<Reference> data, GoType.Visitor<List<Reference>> visitor) {
                            data.add(new StructFieldReference(identifier, type));
                        }

                        @Override
                        public void visitMap(GoTypeMap type, List<Reference> data, GoType.Visitor<List<Reference>> visitor) {
                            data.add(new TypedConstReference(identifier, type.getKeyType()));
                        }
                    }
            ));

            return references.toArray(new PsiReference[references.size()]);
        }

        if (FunctionOrTypeNameReference.MATCHER.accepts(this))
            return new PsiReference[]{new FunctionOrTypeNameReference(this)};

        if (ShortVarReference.SHORT_VAR_DECLARATION.accepts(this))
            return new PsiReference[]{new ShortVarReference(this)};

        if (SELECTOR_MATCHER.accepts(this)) {
            GoSelectorExpression selectorExpression = (GoSelectorExpression) getParent();

            GoType baseTypes[] = selectorExpression.getBaseExpression().getType();
            List<Reference> references = GoTypes.visitFirstType(baseTypes, new GoType.ForwardingVisitor<List<Reference>>(
                    new ArrayList<Reference>(),
                    new GoType.Second<List<Reference>>() {

                        final GoLiteralIdentifier ident = GoLiteralIdentifierImpl.this;

                        @Override
                        public void visitPointer(GoTypePointer type, List<Reference> data, GoType.Visitor<List<Reference>> visitor) {
                            type.getTargetType().accept(visitor);
                        }

                        @Override
                        public void visitPackage(GoTypePackage type, List<Reference> data, GoType.Visitor<List<Reference>> visitor) {
                            GoPackage goPackage = type.getPackage();
                            if (goPackage != GoPackages.C)
                                data.add(new PackageSymbolReference(ident, goPackage));
                        }

                        @Override
                        public void visitName(GoTypeName type, List<Reference> data, GoType.Visitor<List<Reference>> visitor) {
                            data.add(new InterfaceMethodReference(ident, type));
                            data.add(new MethodReference(ident, type));

                            // HACK: I should not have to do this here
                            if (type != type.getUnderlyingType() && !(type.getUnderlyingType() instanceof GoTypeName))
                                type.getUnderlyingType().accept(visitor);
                        }

                        @Override
                        public void visitPrimitive(GoTypePrimitive type, List<Reference> data, GoType.Visitor<List<Reference>> visitor) {
                            data.add(new MethodReference(ident, type));
                        }

                        @Override
                        public void visitStruct(GoTypeStruct type, List<Reference> data, GoType.Visitor<List<Reference>> visitor) {
                            data.add(new StructFieldReference(ident, type));
                        }
                    }
            ));

            return references.toArray(new PsiReference[references.size()]);
        }

        if (VarOrConstReference.MATCHER.accepts(this))
            return new PsiReference[]{new VarOrConstReference(this), new PackageReference(this)};

        if (psiElement(GoLiteralIdentifier.class).insideStarting(psiElement(GoPsiTypeName.class)).accepts(this))
            return new PsiReference[]{new PackageReference(this)};

        return PsiReference.EMPTY_ARRAY;
    }

    @Override
    public PsiElement getNameIdentifier() {
        return this;
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                       @NotNull ResolveState state,
                                       PsiElement lastParent,
                                       @NotNull PsiElement place) {
        return processor.execute(this, state);
    }

    @NotNull
    @Override
    public SearchScope getUseScope() {
        if (GoElementPatterns.GLOBAL_CONST_DECL.accepts(this) ||
                GoElementPatterns.GLOBAL_VAR_DECL.accepts(this) ||
                GoElementPatterns.FUNCTION_DECLARATION.accepts(this) ||
                GoElementPatterns.METHOD_DECLARATION.accepts(this)) {
            return getGlobalElementSearchScope(this, getName());
        }

//        if (isNodeOfType(getParent(), GoElementTypes.LABELED_STATEMENT) ||
//                LabelReference.MATCHER.accepts(this)) {
//            return new LocalSearchScope(
//                    findParentOfType(this, GoFunctionDeclaration.class));
//        }

        return getLocalElementSearchScope(this);
    }

    @Override
    public void setIotaValue(int value) {
        iotaValue = BigInteger.valueOf(value);
    }

    @Override
    public BigInteger getIotaValue() {
        return iotaValue;
    }
}