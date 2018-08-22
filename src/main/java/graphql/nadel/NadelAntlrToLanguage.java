package graphql.nadel;

import graphql.Assert;
import graphql.Internal;
import graphql.language.Definition;
import graphql.language.FieldDefinition;
import graphql.nadel.dsl.FieldDefinitionWithTransformation;
import graphql.nadel.dsl.FieldMappingDefinition;
import graphql.nadel.dsl.FieldTransformation;
import graphql.nadel.dsl.InnerServiceHydration;
import graphql.nadel.dsl.ServiceDefinition;
import graphql.nadel.dsl.StitchingDsl;
import graphql.nadel.parser.GraphqlAntlrToLanguage;
import graphql.nadel.parser.antlr.StitchingDSLParser;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.nadel.dsl.FieldDefinitionWithTransformation.newFieldDefinitionWithTransformation;

@Internal
public class NadelAntlrToLanguage extends GraphqlAntlrToLanguage {


    public NadelAntlrToLanguage(CommonTokenStream tokens) {
        super(tokens);
    }

    public StitchingDsl createStitchingDsl(StitchingDSLParser.StitchingDSLContext ctx) {
        StitchingDsl.Builder stitchingDsl = StitchingDsl.newStitchingDSL();
        List<ServiceDefinition> serviceDefintions = ctx.serviceDefinition().stream()
                .map(this::createServiceDefinition)
                .collect(Collectors.toList());
        stitchingDsl.serviceDefinitions(serviceDefintions);
        return stitchingDsl.build();
    }

    private ServiceDefinition createServiceDefinition(StitchingDSLParser.ServiceDefinitionContext serviceDefinitionContext) {
        ServiceDefinition.Builder builder = ServiceDefinition.newServiceDefinition();
        builder.name(serviceDefinitionContext.name().getText());
        List<Definition> definitions = createTypeSystemDefinitions(serviceDefinitionContext.typeSystemDefinition());
        builder.definitions(definitions);
        return builder.build();
    }


    private List<Definition> createTypeSystemDefinitions(List<StitchingDSLParser.TypeSystemDefinitionContext> typeSystemDefinitionContexts) {
        return typeSystemDefinitionContexts.stream().map(this::createTypeSystemDefinition).collect(Collectors.toList());
    }

    @Override
    protected FieldDefinition createFieldDefinition(StitchingDSLParser.FieldDefinitionContext ctx) {
        FieldDefinition fieldDefinition = super.createFieldDefinition(ctx);
        if (ctx.fieldTransformation() == null) {
            return fieldDefinition;
        }
        FieldDefinitionWithTransformation.Builder builder = newFieldDefinitionWithTransformation(fieldDefinition);
        builder.fieldTransformation(createFieldTransformation(ctx.fieldTransformation()));
        return builder.build();
    }

    private FieldTransformation createFieldTransformation(StitchingDSLParser.FieldTransformationContext ctx) {
        if (ctx.fieldMappingDefinition() != null) {
            return new FieldTransformation(createFieldMappingDefinition(ctx.fieldMappingDefinition()),
                    getSourceLocation(ctx), new ArrayList<>());
        } else if (ctx.innerServiceHydration() != null) {
            return new FieldTransformation(createInnerServiceHydration(ctx.innerServiceHydration()),
                    getSourceLocation(ctx), new ArrayList<>());
        } else {
            return Assert.assertShouldNeverHappen();
        }
    }

    private FieldMappingDefinition createFieldMappingDefinition(StitchingDSLParser.FieldMappingDefinitionContext ctx) {
        return new FieldMappingDefinition(ctx.name().getText(), getSourceLocation(ctx), new ArrayList<>());
    }

    private InnerServiceHydration createInnerServiceHydration(StitchingDSLParser.InnerServiceHydrationContext ctx) {
        String serviceName = ctx.serviceName().getText();
        String topLevelField = ctx.topLevelField().getText();

        Map<String, FieldMappingDefinition> inputMappingDefinitionMap = new LinkedHashMap<>();
        List<StitchingDSLParser.RemoteArgumentPairContext> remoteArgumentPairContexts = ctx.remoteCallDefinition()
                .remoteArgumentPair();
        for (StitchingDSLParser.RemoteArgumentPairContext remoteArgumentPairContext : remoteArgumentPairContexts) {
            inputMappingDefinitionMap.put(remoteArgumentPairContext.name().getText(),
                    createFieldMappingDefinition(remoteArgumentPairContext.fieldMappingDefinition()));
        }
        return new InnerServiceHydration(getSourceLocation(ctx), new ArrayList<>(), serviceName, topLevelField,
                inputMappingDefinitionMap);
    }

}
