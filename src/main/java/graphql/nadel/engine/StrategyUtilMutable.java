package graphql.nadel.engine;

import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.language.Field;
import graphql.nadel.Operation;
import graphql.nadel.execution.ExecutionResultNode;
import graphql.schema.GraphQLSchema;
import graphql.util.FpKit;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;

public class StrategyUtilMutable {

    public static Map<MergedField, List<ExecutionResultNode>> groupNodesIntoBatchesByField(Collection<ExecutionResultNode> nodes, ExecutionResultNode root) {
        Map<MergedField, List<ExecutionResultNode>> nodeByField = FpKit.groupingBy(nodes,
                (node -> node.getMergedField()));
        return nodeByField;
    }


//    public static Set<NodeZipper<ExecutionResultNode>> getHydrationInputNodes(ForkJoinPool forkJoinPool, ExecutionResultNode roots) {
//        Comparator<NodeZipper<ExecutionResultNode>> comparator = (node1, node2) -> {
//            if (node1 == node2) {
//                return 0;
//            }
//            List<Breadcrumb<ExecutionResultNode>> breadcrumbs1 = node1.getBreadcrumbs();
//            List<Breadcrumb<ExecutionResultNode>> breadcrumbs2 = node2.getBreadcrumbs();
//            if (breadcrumbs1.size() != breadcrumbs2.size()) {
//                return Integer.compare(breadcrumbs1.size(), breadcrumbs2.size());
//            }
//            for (int i = breadcrumbs1.size() - 1; i >= 0; i--) {
//                int ix1 = breadcrumbs1.get(i).getLocation().getIndex();
//                int ix2 = breadcrumbs2.get(i).getLocation().getIndex();
//                if (ix1 != ix2) {
//                    return Integer.compare(ix1, ix2);
//                }
//            }
//            return Assert.assertShouldNeverHappen();
//        };
//        Set<NodeZipper<ExecutionResultNode>> result = Collections.synchronizedSet(new TreeSet<>(comparator));
//
//        TreeParallelTraverser<ExecutionResultNode> traverser = TreeParallelTraverser.parallelTraverser(ExecutionResultNode::getChildren, null, forkJoinPool);
//        traverser.traverse(roots, new TraverserVisitorStub<ExecutionResultNode>() {
//            @Override
//            public TraversalControl enter(TraverserContext<ExecutionResultNode> context) {
//                if (context.thisNode() instanceof HydrationInputNodeMutable) {
//                    result.add(new NodeZipper<>(context.thisNode(), context.getBreadcrumbs(), FIX_NAMES_ADAPTER));
//                }
//                return TraversalControl.CONTINUE;
//            }
//
//        });
//        return result;
//    }

    public static ExecutionStepInfo createRootExecutionStepInfo(GraphQLSchema graphQLSchema, Operation operation) {
        ExecutionStepInfo executionInfo = newExecutionStepInfo().type(operation.getRootType(graphQLSchema)).path(ExecutionPath.rootPath()).build();
        return executionInfo;
    }

    public static <T extends ExecutionResultNode> T changeFieldInResultNode(T executionResultNode, Field newField) {
        MergedField mergedField = MergedField.newMergedField(newField).build();
        return (T) changeFieldInResultNode(executionResultNode, mergedField);
    }

    public static <T extends ExecutionResultNode> T changeEsiInResultNode(T executionResultNode, ExecutionStepInfo newEsi) {
        return (T) executionResultNode.withNewExecutionStepInfo(newEsi);
    }

    public static <T extends ExecutionResultNode> T changeFieldInResultNode(T executionResultNode, MergedField mergedField) {
        ExecutionStepInfo newStepInfo = executionResultNode.getExecutionStepInfo().transform(builder -> builder.field(mergedField));
        return (T) executionResultNode.withNewExecutionStepInfo(newStepInfo);
    }

}