package graphql.nadel.normalized

import graphql.GraphQL
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.nadel.testutils.TestUtil
import graphql.schema.GraphQLSchema
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import spock.lang.Specification

import static graphql.nadel.dsl.NodeId.getId

class NormalizedQueryFactoryTest extends Specification {


    def "test"() {
        String schema = """
type Query{ 
    animal: Animal
}
interface Animal {
    name: String
    friends: [Friend]
}

union Pet = Dog | Cat

type Friend {
    name: String
    isBirdOwner: Boolean
    isCatOwner: Boolean
    pets: [Pet] 
}

type Bird implements Animal {
   name: String 
   friends: [Friend]
}

type Cat implements Animal{
   name: String 
   friends: [Friend]
   breed: String 
}

type Dog implements Animal{
   name: String 
   breed: String
   friends: [Friend]
}
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            animal{
                name
                otherName: name
               ... on Cat {
                    name
                    friends {
                        ... on Friend {
                            isCatOwner
                        }
                   } 
               }
               ... on Bird {
                    friends {
                        isBirdOwner
                    }
                    friends {
                        name
                    }
               }
               ... on Dog {
                  name   
               }
        }}
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.animal: Animal (conditional: false)',
                        'Bird.name: String (conditional: true)',
                        'Cat.name: String (conditional: true)',
                        'Dog.name: String (conditional: true)',
                        'otherName: Bird.name: String (conditional: true)',
                        'otherName: Cat.name: String (conditional: true)',
                        'otherName: Dog.name: String (conditional: true)',
                        'Cat.friends: [Friend] (conditional: false)',
                        'Friend.isCatOwner: Boolean (conditional: false)',
                        'Bird.friends: [Friend] (conditional: false)',
                        'Friend.isBirdOwner: Boolean (conditional: false)',
                        'Friend.name: String (conditional: false)']

    }

    def "test2"() {
        String schema = """
        type Query{ 
            a: A
        }
        interface A {
           b: B  
        }
        type A1 implements A {
           b: B 
        }
        type A2 implements A{
            b: B
        }
        interface B {
            leaf: String
        }
        type B1 implements B {
            leaf: String
        } 
        type B2 implements B {
            leaf: String
        } 
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            a {
            ... on A {
                myAlias: b { leaf }
            }
                ... on A1 {
                   b { 
                     ... on B1 {
                        leaf
                        }
                     ... on B2 {
                        leaf
                        }
                   }
                }
                ... on A1 {
                   b { 
                     ... on B1 {
                        leaf
                        }
                   }
                }
                ... on A2 {
                    b {
                       ... on B2 {
                            leaf
                       } 
                    }
                }
            }
        }
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.a: A (conditional: false)',
                        'myAlias: A1.b: B (conditional: true)',
                        'B1.leaf: String (conditional: true)',
                        'B2.leaf: String (conditional: true)',
                        'myAlias: A2.b: B (conditional: true)',
                        'B1.leaf: String (conditional: true)',
                        'B2.leaf: String (conditional: true)',
                        'A1.b: B (conditional: false)',
                        'B1.leaf: String (conditional: false)',
                        'B2.leaf: String (conditional: false)',
                        'A2.b: B (conditional: false)',
                        'B2.leaf: String (conditional: false)']
    }

    def "test3"() {
        String schema = """
        type Query{ 
            a: [A]
            object: Object
        }
        type Object {
            someValue: String
        }
        interface A {
           b: B  
        }
        type A1 implements A {
           b: B 
        }
        type A2 implements A{
            b: B
        }
        interface B {
            leaf: String
        }
        type B1 implements B {
            leaf: String
        } 
        type B2 implements B {
            leaf: String
        } 
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
          object{someValue}
          a {
            ... on A1 {
              b {
                ... on B {
                  leaf
                }
                ... on B1 {
                  leaf
                }
                ... on B2 {
                  ... on B {
                    leaf
                  }
                  leaf
                  leaf
                  ... on B2 {
                    leaf
                  }
                }
              }
            }
          }
        }
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.object: Object (conditional: false)',
                        'Object.someValue: String (conditional: false)',
                        'Query.a: [A] (conditional: false)',
                        'A1.b: B (conditional: false)',
                        'B1.leaf: String (conditional: true)',
                        'B2.leaf: String (conditional: true)']

    }

    def "test impossible type condition"() {

        String schema = """
        type Query{ 
            pets: [Pet]
        }
        interface Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        type Dog implements Pet{
            name: String
        }
        union CatOrDog = Cat | Dog
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                ... on Dog {
                    ... on CatOrDog {
                    ... on Cat{
                            name
                            }
                    }
                }
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets: [Pet] (conditional: false)']

    }

    def "query with unions and __typename"() {

        String schema = """
        type Query{ 
            pets: [CatOrDog]
        }
        type Cat {
            catName: String
        }
        type Dog {
            dogName: String
        }
        union CatOrDog = Cat | Dog
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                __typename
                ... on Cat {
                    catName 
                }  
                ... on Dog {
                    dogName
                }
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        //TODO: this is not really correct: should be conditional
        printedTree == ['Query.pets: [CatOrDog] (conditional: false)',
                        'Cat.catName: String (conditional: false)',
                        'Dog.dogName: String (conditional: false)']

    }

    def "test5"() {
        String schema = """
        type Query{ 
            a: [A]
        }
        interface A {
           b: String
        }
        type A1 implements A {
           b: String 
        }
        type A2 implements A{
            b: String
            otherField: A
        }
        type A3  implements A {
            b: String
        }
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)


        def query = """
        {
            a {
                b
                ... on A1 {
                   b 
                }
                ... on A2 {
                    b 
                    otherField {
                    ... on A2 {
                            b
                        }
                        ... on A3 {
                            b
                        }
                    }
                    
                }
            }
        }
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.a: [A] (conditional: false)',
                        'A1.b: String (conditional: true)',
                        'A2.b: String (conditional: true)',
                        'A3.b: String (conditional: true)',
                        'A2.otherField: A (conditional: false)',
                        'A2.b: String (conditional: false)',
                        'A3.b: String (conditional: false)']

    }

    def "test6"() {
        String schema = """
        type Query {
            issues: [Issue]
        }

        type Issue {
            id: ID
            author: User
        }
        type User {
            name: String
            createdIssues: [Issue] 
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        def query = """{ issues {
                    author {
                        name
                        ... on User {
                            createdIssues {
                                id
                            }
                        }
                    }
                }}
                """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.issues: [Issue] (conditional: false)',
                        'Issue.author: User (conditional: false)',
                        'User.name: String (conditional: false)',
                        'User.createdIssues: [Issue] (conditional: false)',
                        'Issue.id: ID (conditional: false)']

    }

    def "test7"() {
        String schema = """
        type Query {
            issues: [Issue]
        }

        type Issue {
            authors: [User]
        }
        type User {
            name: String
            friends: [User]
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        def query = """{ issues {
                    authors {
                       friends {
                            friends {
                                name
                            }
                       } 
                   }
                }}
                """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.issues: [Issue] (conditional: false)',
                        'Issue.authors: [User] (conditional: false)',
                        'User.friends: [User] (conditional: false)',
                        'User.friends: [User] (conditional: false)',
                        'User.name: String (conditional: false)']

    }

    def "query with fragment definition"() {
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.foo: Foo (conditional: false)',
                        'Foo.subFoo: String (conditional: false)',
                        'Foo.moreFoos: Foo (conditional: false)',
                        'Foo.subFoo: String (conditional: false)']
    }

    def "print the original query"() {
        given:
        String schema = """
        type Query {
            issues: [Issue]
        }

        type Issue {
            id: ID
            author: User
        }
        type User {
            name: String
            createdIssues: [Issue] 
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        def query = """{ 
                issues {
                    author {
                        name
                        ... on User {
                            createdIssues {
                                ... on Issue {
                                    id
                                }
                            }
                        }
                        ... on User {
                            name
                        }
                    }
                }
                myIssues : issues {
                    myAuthor: author {
                        ... on User {
                            name
                        }
                        name
                    }
                }
            }
                """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def normalizedQuery = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])

        when:
        String originalQuery = normalizedQuery.printOriginalQuery();

        then:
        originalQuery == """{
  issues {
    author {
      name
      ... on User {
        createdIssues {
          ... on Issue {
            id
          }
        }
      }
      ... on User {
        name
      }
    }
  }
  myIssues: issues {
    myAuthor: author {
      ... on User {
        name
      }
      name
    }
  }
}"""

    }

    List<String> printTree(NormalizedQuery queryExecutionTree) {
        def result = []
        Traverser<NormalizedQueryField> traverser = Traverser.depthFirst({ it.getChildren() });
        traverser.traverse(queryExecutionTree.getRootFields(), new TraverserVisitorStub<NormalizedQueryField>() {
            @Override
            TraversalControl enter(TraverserContext<NormalizedQueryField> context) {
                NormalizedQueryField queryExecutionField = context.thisNode();
                result << queryExecutionField.print()
                return TraversalControl.CONTINUE;
            }
        });
        result
    }

    def "normalized fields map by field id is build"() {
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        def subFooField = (document.getDefinitions()[1] as FragmentDefinition).getSelectionSet().getSelections()[0] as Field
        def subFooFieldId = getId(subFooField)

        NormalizedQueryFactory dependencyGraph = new NormalizedQueryFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def normalizedFieldsByFieldId = tree.getNormalizedFieldsByFieldId()

        expect:
        normalizedFieldsByFieldId.size() == 3
        normalizedFieldsByFieldId.get(subFooFieldId).size() == 2
        normalizedFieldsByFieldId.get(subFooFieldId)[0].level == 2
        normalizedFieldsByFieldId.get(subFooFieldId)[1].level == 3
    }

    private void assertValidQuery(GraphQLSchema graphQLSchema, String query) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();
        assert graphQL.execute(query).errors.size() == 0
    }
}
