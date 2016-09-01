package org.hablapps.gist
import org.scalatest._

/*
The purpose of this gist is explaining what are church encodings of data types,
and how can we implement functions that use pattern matching over them. We will 
use the common domain of arithmetic expressions to illustrate our findings. 

Throughout the code some references will be made to the deep encoding of arithmetic 
expressions using ADTs. You can find the relevant code in this gist: 

https://github.com/hablapps/gist/blob/master/src/test/scala/ADTs.scala

For the most part, this gist can be considered as an Scala translation of this post:

http://okmij.org/ftp/tagless-final/course/Boehm-Berarducci.html

*/
class ChurchEncodings extends FlatSpec with Matchers{ 

  /*
  In embedded DSLs we distinguished between "data" and "functions". We'll
  see now that this distinction is somewhat artificial, and data too can be
  also regarded as purely functional, i.e. data can be represented by functions
  alone.
  */
  object Church{
    
    /*
    The key to thinking about data as functions is considering that the essence 
    of data types are their constructors, and these constructors are, of course, 
    functions. For instance, the constructors of the `Expr` ADT were `Lit`, `Neg`
    and `Add`. These constructors are automatically generated by Scala through
    the companion object of the `case class`. The types of these constructors
    are `Lit: Int => Expr`, `Neg: Expr => Expr` and `Add: (Expr, Expr) => Expr`.

    A Church encoding represent data types through its constructors. But, we can't
    make reference to the particular `Expr` ADT, of course, so we abstract away 
    from any particular representation. The resulting Church encoding (actually, 
    the Boehm-Berarducci encoding, since the Church encoding refers to the untyped 
    lambda-calculus) is: 
    */

    trait Expr{
      def apply[E](lit: Int => E, neg: E => E, add: (E,E) => E): E
    }
    
    /*
    Note the similarity with the implementation of the `fold` recursion scheme for
    the `Expr` ADTs. Indeed, Church encodings implement data as folds. However, as you can 
    see, the new `Expr` type is still represented by a class (particularly, a `trait`). 
    After all, this is Scala and any data type has to be represented as a class of objects. 
    The only class member of this class is a polymorphic function that allows us to create 
    objects of an arbitrary type `E`, using generic versions of the constructors that we introduced
    in our `Expr` ADTs. 

    Let's see some examples of arithmetic expressions represented as Church encodings.
    These values represent the expressions "(1+(-2))" and "(-(-2))", respectively.
    And they do it in a rather generic fashion, i.e. in a completely independent way of the 
    many possible types `E` that we may alternatively choose to represent our expressions. 
    */

    val e1: Expr = new Expr{
      def apply[E](lit: Int => E, neg: E => E, add: (E,E) => E): E = 
        add(lit(1), neg(lit(2)))
    }

    val e2: Expr = new Expr{
      def apply[E](lit: Int => E, neg: E => E, add: (E,E) => E): E = 
        neg(neg(lit(2))) 
    }

    /*
    In a sense, values `e1` and `e2` are canonical ways of representing arithmetic
    expressions, and subsume any other possible representation. Thus, we can 
    use these generic values as "recipes" that will allow us to create expressions 
    written using concrete representations. We do this simply by passing their corresponding 
    constructors to the polymorphic function (the actual "recipe"). 

    For instance, we can create values of the ADT representation as follows:
    */
    
    val e1_ADT: ADTs.Expr = e1(ADTs.Lit, ADTs.Neg, ADTs.Add)
    val e2_ADT: ADTs.Expr = e2(ADTs.Lit, ADTs.Neg, ADTs.Add)

    e1_ADT shouldBe ADTs.Add(ADTs.Lit(1), ADTs.Neg(ADTs.Lit(2)))
    e2_ADT shouldBe ADTs.Neg(ADTs.Neg(ADTs.Lit(2)))

    /*
    An alternative way of creating Church expressions is by using smart constructors
    that instantiate the trait `Expr` for us. 
    */

    object Expr{
      def lit(i: Int): Expr = new Expr{
        def apply[E](lit: Int => E, neg: E => E, add: (E,E) => E): E = 
          lit(i)
      }

      def neg(e: Expr): Expr = new Expr{
        def apply[E](lit: Int => E, neg: E => E, add: (E,E) => E): E = 
          neg(e(lit,neg,add))
      }

      def add(e1: Expr, e2: Expr): Expr = new Expr{
        def apply[E](lit: Int => E, neg: E => E, add: (E,E) => E): E = 
          add(e1(lit,neg,add), e2(lit,neg,add))
      }
    }

    /*
    Using these constructors we can write arithmetic expressions in a very concise and 
    elegant manner (just as we wrote them with the ADT's constructors). On the other hand, 
    they create many intermediate objects which may not be necessary at all. 
    */
    import Expr.{lit, neg, add}
    val e1_v2: Expr = add(lit(1), neg(lit(2)))

    e1_v2(ADTs.Lit, ADTs.Neg, ADTs.Add) shouldBe ADTs.Add(ADTs.Lit(1), ADTs.Neg(ADTs.Lit(2)))

    /* 
    What should it happen if we apply the smart constructors of the Church encoding itself
    to a Church value. Well, we should obtain a Church value, and that value shouldBe
    equivalent to the original one. In order to test the equivalence of two Church values 
    we test the equality of the resulting value when applied to a concrete representation.
    */

    val e1_v3: Expr = e1(lit, neg, add)
    e1_v3(ADTs.Lit, ADTs.Neg, ADTs.Add) shouldBe e1(ADTs.Lit, ADTs.Neg, ADTs.Add)
  }

  // Let's actually test the previous checks
  "Church encondings" should "work" in Church

  /*
  Ok, that's for values. But, how can we represent the interpreters `eval`, `write`, etc., 
  that we implemented for the ADT representation? We start by considering compositional 
  interpreters, which is the easy case. Indeed, since Church expressions are simply folds
  the new implementations recall almost exactly the implementations that we made for the
  `Expr` ADTs.
  */
  object CompositionalInterpreters{
    import Church._, Expr._

    // Evaluation 

    def eval(e: Expr): Int = e[Int](
      i => i,
      e1 => -e1,
      (e1,e2) => e1 + e2
    )

    eval(add(lit(1),lit(2))) shouldBe 3

    // Printing 
    
    def write(e: Expr): String = e[String](
      i => s"$i",
      e => s"(-$e)",
      (e1,e2) => s"($e1+$e2)"
    )

    write(add(lit(1),lit(2))) shouldBe "(1+2)"
  }

  "Church functions" should "work" in CompositionalInterpreters

  /* 
  What about non-compositional interpreters? Can we pattern match Church expressions in the
  same way that we did for ADTs? It turns out that we can! First of all, note that in order
  to apply pattern matching we should be able to represent two things: first, the kind of 
  expression we are dealing with, i.e. have we received a simple literal, a negated expression 
  or a sum of expressions? These are the different "cases"; second, we have to represent 
  what we want to do in each "case". 
  */
  object DeconstructingChurch{
    import Church._, Expr._

    /*
    The following type actually encodes all the information we need to pattern 
    match.
    */
    trait Match{
      def apply[W](dlit: Int => W, dneg: Expr => W, dadd: (Expr, Expr) => W): W
    }

    /*
    First, note that in order to instantiate this trait we have to implement its 
    polymorphic function. And the only way to implement this function, i.e. obtaining
    a value of type `W`, is by using *one* of the arguments `dlit`, `dneg` or `dadd`. 
    But in using one of these arguments we will have to provide either an `Int`, and 
    expression `Expr`, or a pair of expressions. Hence, an instance of this type `Match`
    somehow encodes the information we need for pattern matching concerning the kind of
    expression we are dealing with. For instance:
    */

    // A match of the expression `lit(1)`
    val litM1: Match = new Match{
      def apply[W](dlit: Int => W, dneg: Expr => W, dadd: (Expr, Expr) => W): W = 
        dlit(1) // Note how the interger represented by this match is simply 
                // encoded as an argument of the function `dlit`
    }

    // A match of the expression `neg(lit(1))`
    val negM1: Match = new Match{
      def apply[W](dlit: Int => W, dneg: Expr => W, dadd: (Expr, Expr) => W): W = 
        dneg(lit(1))
    }

    // A match of the expression `add(lit(1), lit(2))`
    val addM1: Match = new Match{
      def apply[W](dlit: Int => W, dneg: Expr => W, dadd: (Expr, Expr) => W): W = 
        dadd(lit(1),lit(2))
    }

    /* 
    Second, note that the "things" that we want to do for each different case of 
    the pattern match are represented by the functions `dlit`, `dneg` and `dadd` 
    themselves (you can look at these functions as the possible "continuations"). 
    For instance, let's say that we want simply to return 1 if the match represents 
    a literal, 2 if it represents a negated expression, and 3 if it represents a sum. 
    */

    litM1(_ => 1, _  => 2, (_,_) => 3) shouldBe 1
    negM1(_ => 1, _  => 2, (_,_) => 3) shouldBe 2
    addM1(_ => 1, _  => 2, (_,_) => 3) shouldBe 3

    /*
    Another interesting example that we will use later on is reconstructing the 
    original expression being matched:
    */

    val lit1: Expr = litM1(lit, neg, add)
    val neg1: Expr = negM1(lit, neg, add)
    val add1: Expr = addM1(lit, neg, add)

    import CompositionalInterpreters._

    write(lit1) shouldBe "1"
    write(neg1) shouldBe "(-1)"
    write(add1) shouldBe "(1+2)"

    /*
    Given all this, the only thing that we need now in order to implement pattern 
    matching-based functions over Church encodings is some way of obtaining for some 
    arbitrary expression its corresponding match information, i.e. a function with signature
    `match: Expr => Match`. But the only way to implement this function is as a fold,
    so we need to find functions `Int => Match`, `Match => Match` and `(Match,Match)
    => Match` that can be passed to our expression.
    */

    def `match`(e: Expr): Match = 
      e(Match.lit, Match.neg, Match.add)

    /* 
    In other words, in order to implement that function as a fold, we have to find 
    a way of obtaining a match for literals, a match for negated expressions taking 
    into account the match of the negated expression, and a match for sums taking 
    into account the matches of the corresponding subexpressions. 

    We implement these functions as part of the companion object for the `Match` type.
    */
    object Match{

      def lit(i: Int): Match = new Match{
        def apply[W](dlit: Int => W, dneg: Expr => W, dadd: (Expr, Expr) => W): W =
          dlit(i)
      }

      def neg(e: Match): Match = new Match{
        def apply[W](dlit: Int => W, dneg: Expr => W, dadd: (Expr, Expr) => W): W =
          dneg(e(Expr.lit, Expr.neg, Expr.add))
            // Note how this match encodes the subexpression being negated, and
            // how we obtain this subexpression from its corresponding match.
      }

      def add(e1: Match, e2: Match): Match = new Match{
        def apply[W](dlit: Int => W, dneg: Expr => W, dadd: (Expr, Expr) => W): W =
          dadd(e1(Expr.lit, Expr.neg, Expr.add),e2(Expr.lit, Expr.neg, Expr.add))
      }
    }

    /*
    Now we are ready to implement both compositional and non-compositional interpreters
    for Church-encoded expressions.
    */

    def write(e: Expr): String = 
      `match`(e)(
        i => s"$i", 
        e1 => "(-"+write(e1)+")",
        (e1, e2) => "("+write(e1)+"+"+write(e2)+")"
      )

    write(lit(1)) shouldBe "1"
    write(neg(lit(1))) shouldBe "(-1)"
    write(add(lit(1),lit(2))) shouldBe "(1+2)"

    /*
    Note how we pattern match the inner expression `e1` in the negated case for the `pushNeg`
    interpreter.
    */
    def pushNeg(e: Expr): Expr = 
      `match`(e)(
        _ => e, 
        e1 => `match`(e1)(
          _ => e, 
          e2 => pushNeg(e2), 
          (e2, e3) => add(pushNeg(neg(e2)), pushNeg(neg(e3)))
        ), 
        (e1, e2) => add(pushNeg(e1),pushNeg(e2))
      )

    import ADTs.{Lit, Neg, Add}

    pushNeg(neg(lit(1)))(Lit, Neg, Add) should 
      be(Neg(Lit(1)))

    pushNeg(neg(add(neg(lit(1)), lit(2))))(Lit, Neg, Add) should 
      be(Add(Lit(1), Neg(Lit(2))))
  }

  "Deconstructing Church" should "work" in DeconstructingChurch

  /*
  The last part of this gist will simply add some syntactic sugar to the above
  code, so that we can implement recursive functions over Church encodings exactly
  as we do with ADT-based representations.
  */
  object ScalaExtractors{
    import Church._, Expr._, DeconstructingChurch.{pushNeg => _, _}

    /*
    In order to achieve this extra level of conciseness and clarity, we use
    Scala extractors. These are given to us by the Scala compiler each time
    we implement a case class. Since we did not implement `Expr` as a case 
    class, we have to implement them ourselves. 
    */

    object Lit{
      def unapply(e: Expr): Option[Int] = 
        `match`(e)(i => Some(1), _ => None, (_,_) => None)
    }

    object Neg{
      def unapply(e: Expr): Option[Expr] = 
        `match`(e)(i => None, e1 => Some(e1), (_,_) => None)
    }

    object Add{
      def unapply(e: Expr): Option[(Expr, Expr)] = 
        `match`(e)(_ => None, _ => None, (e1, e2) => Some((e1,e2)))
    }

    /*
    With these extractors we can implement the `pushNeg` interpreter in a more
    familiar way.
    */
    
    def pushNeg(e: Expr): Expr = e match {
      case Lit(_) => e
      case Neg(Lit(_)) => e
      case Neg(Neg(e1)) => pushNeg(e1)
      case Neg(Add(e1,e2)) => add(pushNeg(neg(e1)), pushNeg(neg(e2)))
      case Add(e1,e2) => add(pushNeg(e1),pushNeg(e2))
    }

    write(pushNeg(neg(neg(lit(1))))) shouldBe "1"
    write(pushNeg(neg(add(neg(lit(1)),lit(2))))) shouldBe "(1+(-2))"
  }

  "ScalaExtractors" should "work" in ScalaExtractors
      
} 

object ChurchEncodings extends ChurchEncodings
