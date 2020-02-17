package graphql.nadel.engine;

import graphql.GraphQLError;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution.FetchedValue;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.nextgen.ExecutionStrategyUtil;
import graphql.execution.nextgen.FetchedValueAnalysis;
import graphql.execution.nextgen.FieldSubSelection;
import graphql.execution.nextgen.result.ResolvedValue;
import graphql.nadel.ServiceExecutionResult;
import graphql.nadel.execution.ExecutionResultNode;
import graphql.nadel.execution.ObjectExecutionResultNode;
import graphql.nadel.execution.ResultNodeTraverser;
import graphql.nadel.execution.ResultNodesCreator;
import graphql.nadel.execution.RootExecutionResultNode;
import graphql.nadel.execution.UnresolvedObjectResultNode;
import graphql.nadel.util.ErrorUtil;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;
import graphql.util.TraverserVisitorStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.util.FpKit.map;


public class ServiceResultToResultNodesMutable {

    private final ExecutionStepInfoFactory executionStepInfoFactory = new ExecutionStepInfoFactory();
    private final ServiceExecutionResultAnalyzer fetchedValueAnalyzer = new ServiceExecutionResultAnalyzer();
    private final ResultNodesCreator resultNodesCreator = new ResultNodesCreator();
    private final ExecutionStrategyUtil util = new ExecutionStrategyUtil();
    ResultNodesTransformer resultNodesTransformer = new ResultNodesTransformer();

    private static final Logger log = LoggerFactory.getLogger(ServiceResultToResultNodesMutable.class);

    public RootExecutionResultNode resultToResultNode(ExecutionContext executionContext,
                                                      ExecutionStepInfo executionStepInfo,
                                                      List<MergedField> mergedFields,
                                                      ServiceExecutionResult serviceExecutionResult) {
        long startTime = System.currentTimeMillis();

        List<GraphQLError> errors = ErrorUtil.createGraphQlErrorsFromRawErrors(serviceExecutionResult.getErrors());
        RootExecutionResultNode rootNode = new RootExecutionResultNode(Collections.emptyList(), errors);

        final int[] nodeCounter = {0};
        ResultNodeTraverser resultNodeTraverser = ResultNodeTraverser.depthFirst();
        TraverserVisitor<ExecutionResultNode> traverserVisitor = new TraverserVisitorStub<ExecutionResultNode>() {
            @Override
            public TraversalControl enter(TraverserContext<ExecutionResultNode> context) {
                nodeCounter[0]++;
                ExecutionResultNode node = context.thisNode();
                if (node instanceof RootExecutionResultNode) {
                    changeRootNode(context, (RootExecutionResultNode) node, executionContext, executionStepInfo, mergedFields, serviceExecutionResult);
                    return TraversalControl.CONTINUE;
                }
                if (!(node instanceof UnresolvedObjectResultNode)) {
                    return TraversalControl.CONTINUE;
                }
                UnresolvedObjectResultNode unresolvedNode = (UnresolvedObjectResultNode) node;
                ResolvedValue resolvedValue = unresolvedNode.getResolvedValue();
                ExecutionStepInfo esi = unresolvedNode.getExecutionStepInfo();
                FieldSubSelection fieldSubSelection = util.createFieldSubSelection(executionContext, esi, resolvedValue);
                List<ExecutionResultNode> children = fetchSubSelection(executionContext, fieldSubSelection);
                ObjectExecutionResultNode objectExecutionResultNode = new ObjectExecutionResultNode(esi, resolvedValue, children);

                ExecutionResultNode parent = context.getParentNode();
                int index = context.getBreadcrumbs().get(0).getLocation().getIndex();
                parent.replaceChild(index, objectExecutionResultNode);
                context.changeNode(objectExecutionResultNode);
                return TraversalControl.CONTINUE;
            }
        };
        resultNodeTraverser.traverse(traverserVisitor, rootNode);
//        System.out.println("node counter2: " + nodeCounter[0]);
        return rootNode;
    }

    private void changeRootNode(TraverserContext<ExecutionResultNode> context,
                                RootExecutionResultNode rootNode,
                                ExecutionContext executionContext,
                                ExecutionStepInfo executionStepInfo,
                                List<MergedField> mergedFields,
                                ServiceExecutionResult serviceExecutionResult) {
        Map<String, MergedField> subFields = FpKit.getByName(mergedFields, MergedField::getResultKey);
        MergedSelectionSet mergedSelectionSet = MergedSelectionSet.newMergedSelectionSet()
                .subFields(subFields).build();

        FieldSubSelection fieldSubSelectionWithData = FieldSubSelection.newFieldSubSelection().
                executionInfo(executionStepInfo)
                .source(serviceExecutionResult.getData())
                .mergedSelectionSet(mergedSelectionSet)
                .build();

        List<ExecutionResultNode> subNodes = fetchSubSelection(executionContext, fieldSubSelectionWithData);
        rootNode.setChildren(subNodes);
        context.changeNode(rootNode);
    }

    private List<ExecutionResultNode> fetchSubSelection(ExecutionContext executionContext, FieldSubSelection fieldSubSelection) {
        List<FetchedValueAnalysis> fetchedValueAnalysisList = fetchAndAnalyze(executionContext, fieldSubSelection);
        return fetchedValueAnalysisToNodes(executionContext, fetchedValueAnalysisList);
    }

    private List<FetchedValueAnalysis> fetchAndAnalyze(ExecutionContext context, FieldSubSelection fieldSubSelection) {
        return map(fieldSubSelection.getMergedSelectionSet().getSubFieldsList(),
                mergedField -> fetchAndAnalyzeField(context, fieldSubSelection.getSource(), mergedField, fieldSubSelection.getExecutionStepInfo()));
    }

    private FetchedValueAnalysis fetchAndAnalyzeField(ExecutionContext context,
                                                      Object source,
                                                      MergedField mergedField,
                                                      ExecutionStepInfo executionStepInfo) {
        ExecutionStepInfo newExecutionStepInfo = executionStepInfoFactory.newExecutionStepInfoForSubField(context, mergedField, executionStepInfo);
        FetchedValue fetchedValue = fetchValue(source, mergedField);
        return analyseValue(context, fetchedValue, newExecutionStepInfo);
    }

    private FetchedValue fetchValue(Object source, MergedField mergedField) {
        if (source == null) {
            return FetchedValue.newFetchedValue()
                    .fetchedValue(null)
                    .rawFetchedValue(null)
                    .errors(Collections.emptyList())
                    .build();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) source;
        Object fetchedValue = map.get(mergedField.getResultKey());
        return FetchedValue.newFetchedValue()
                .fetchedValue(fetchedValue)
                .rawFetchedValue(fetchedValue)
                .errors(Collections.emptyList())
                .build();
    }

    private FetchedValueAnalysis analyseValue(ExecutionContext executionContext, FetchedValue fetchedValue, ExecutionStepInfo executionInfo) {
        return fetchedValueAnalyzer.analyzeFetchedValue(executionContext, fetchedValue, executionInfo);
    }

    private List<ExecutionResultNode> fetchedValueAnalysisToNodes(ExecutionContext executionContext, List<FetchedValueAnalysis> fetchedValueAnalysisList) {
        return map(fetchedValueAnalysisList, resultNodesCreator::createResultNode);
    }


}