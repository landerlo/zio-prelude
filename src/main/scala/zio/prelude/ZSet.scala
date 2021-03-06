package zio.prelude

import scala.annotation.unchecked.uncheckedVariance

import zio.prelude.newtypes.{ Max, Min, Prod, Sum }

/**
 * A `ZSet[A, B]` is a set of `A` values where `B` represents some notion of
 * "how many" `A` values are included in the set. This can be the number of
 * times each element appears in the set if `B` is a natural number, the
 * probability associated with an element in the set if `B` is a rational
 * number, or even whether an element appears at all if `B` is a boolean.
 */
final class ZSet[+A, +B] private (private val map: Map[A @uncheckedVariance, B]) { self =>

  /**
   * A symbolic alias for `intersect`.
   */
  def &[A1 >: A, B1 >: B](
    that: ZSet[A1, B1]
  )(implicit ev1: Commutative[Min[B1]], ev2: Identity[Sum[B1]]): ZSet[A1, B1] =
    self intersect that

  /**
   * A symbolic alias for `diff`.
   */
  def &~[A1 >: A, B1 >: B](that: ZSet[A1, B1])(implicit ev: Inverse[Sum[B1]]): ZSet[A1, B1] =
    self diff that

  /**
   * A symbolic alias for `zip`.
   */
  def <*>[B1 >: B, C](
    that: ZSet[C, B1]
  )(implicit ev1: Commutative[Sum[B1]], ev2: Commutative[Prod[B1]]): ZSet[(A, C), B1] =
    self zip that

  /**
   * A symbolic alias for `combine`.
   */
  def <>[A1 >: A, B1 >: B](that: ZSet[A1, B1])(implicit ev: Commutative[Sum[B1]]): ZSet[A1, B1] =
    self combine that

  /**
   * A symbolic alias for `union`.
   */
  def |[A1 >: A, B1 >: B](
    that: ZSet[A1, B1]
  )(implicit ev1: Commutative[Max[B1]], ev2: Identity[Sum[B1]]): ZSet[A1, B1] =
    self union that

  /**
   * Returns the number of times the specified element appears in the set.
   */
  def apply[A1 >: A, B1 >: B](a: A1)(implicit ev: Identity[Sum[B1]]): B1 =
    map.asInstanceOf[Map[A1, B1]].getOrElse(a, ev.identity)

  /**
   * Combines this set with the specified set to produce a new set where the
   * number of times each element appears is the sum of the number of times it
   * appears in this set and the specified set.
   */
  def combine[A1 >: A, B1 >: B](that: ZSet[A1, B1])(implicit ev: Commutative[Sum[B1]]): ZSet[A1, B1] =
    ZSet {
      that.map.foldLeft(self.map.asInstanceOf[Map[A1, B1]]) {
        case (map, (a, b1)) =>
          map.get(a) match {
            case Some(b) => map + (a -> ev.combine(Sum(b), Sum(b1)))
            case None    => map + (a -> b1)
          }
      }
    }

  /**
   * Combines this set with the specified set to produce a new set where the
   * number of times each element appears is the difference between the number
   * of times it appears in this set and the specified set.
   */
  def diff[A1 >: A, B1 >: B](that: ZSet[A1, B1])(implicit ev: Inverse[Sum[B1]]): ZSet[A1, B1] =
    ZSet {
      that.map.foldLeft(self.map.asInstanceOf[Map[A1, B1]]) {
        case (map, (a, b1)) =>
          map.get(a) match {
            case Some(b) => map + (a -> ev.inverse(Sum(b), Sum(b1)))
            case None    => map + (a -> ev.inverse(ev.identity, Sum(b1)))
          }
      }
    }

  /**
   * Returns whether this set is equal to the specified set, meaning that the
   * same elements appear in both sets the same number of times.
   */
  override def equals(that: Any): Boolean =
    that match {
      case that: ZSet[_, _] => self.map == that.map
      case _                => false
    }

  /**
   * Creates a new set for each element in this set and combines the resulting
   * sets together. The number of times each element appears will be the sum
   * of the products of the number of times it appeared in the original set
   * and the number of times it appears in each new set.
   */
  def flatMap[B1 >: B, C](
    f: A => ZSet[C, B1]
  )(implicit ev1: Commutative[Sum[B1]], ev2: Commutative[Prod[B1]]): ZSet[C, B1] =
    map.foldLeft[ZSet[C, B1]](ZSet.empty) {
      case (set, (a, b)) =>
        set <> f(a).transform(b1 => ev2.combine(Prod(b), Prod(b1)))
    }

  /**
   * Returns the hash code of this set.
   */
  override final def hashCode: Int =
    map.hashCode

  /**
   * Combines this set with the specified set to produce a new set where the
   * number of times each element appears is the minimum of the number of times
   * it appears in this set and the specified set.
   */
  def intersect[A1 >: A, B1 >: B](
    that: ZSet[A1, B1]
  )(implicit ev1: Commutative[Min[B1]], ev2: Identity[Sum[B1]]): ZSet[A1, B1] =
    ZSet {
      (self.map.toVector ++ that.map.toVector).foldLeft(Map.empty[A1, B1]) {
        case (map, (a, b)) =>
          map + (a -> ev1.combine(Min(map.getOrElse(a, ev2.identity)), Min(b)))
      }
    }

  /**
   * Transforms the elements in the set using the specified function. If this
   * results in mapping two or more elements to the same values, the number of
   * times the new value appears in the set will be the sum of the number of
   * times each of the old values appeared in the set.
   */
  def map[B1 >: B, C](f: A => C)(implicit ev: Commutative[Sum[B1]]): ZSet[C, B1] =
    ZSet {
      map.foldLeft[Map[C, B1]](Map.empty) {
        case (map, (a, b1)) =>
          val c = f(a)
          map.get(c) match {
            case Some(b) => map + (c -> ev.combine(Sum(b), Sum(b1)))
            case None    => map + (c -> b1)
          }
      }
    }

  /**
   * Transforms the representation of how many times each element appears in
   * the set with the specified function.
   */
  def transform[C](f: B => C): ZSet[A, C] =
    ZSet {
      map.map { case (a, b) => (a, f(b)) }
    }

  /**
   * Converts this set to a `Map` from elements to how many times they appear
   * in the set.
   */
  def toMap[A1 >: A]: Map[A1, B] =
    map.asInstanceOf[Map[A1, B]]

  /**
   * Converts this set to a `Set`, discarding information about how many times
   * an element appears in the set beyond whether it appears at all.
   */
  def toSet[A1 >: A, B1 >: B](implicit ev1: Equal[B1], ev2: Identity[Sum[B1]]): Set[A1] =
    map.foldLeft(Set.empty[A1]) {
      case (set, (a, b)) =>
        if (ev1.notEqual(b, ev2.identity)) set + a else set
    }

  /**
   * Returns a meaningful string representation of this set.
   */
  override def toString: String =
    map.mkString("ZSet(", ", ", ")")

  /**
   * Combines this set with the specified set to produce a new set where the
   * number of times each element appears is the maximum of the number of times
   * it appears in this set and the specified set.
   */
  def union[A1 >: A, B1 >: B](
    that: ZSet[A1, B1]
  )(implicit ev1: Commutative[Max[B1]], ev2: Identity[Sum[B1]]): ZSet[A1, B1] =
    ZSet {
      (self.map.toVector ++ that.map.toVector).foldLeft(Map.empty[A1, B1]) {
        case (map, (a, b)) =>
          map + (a -> ev1.combine(Max(map.getOrElse(a, ev2.identity)), Max(b)))
      }
    }

  /**
   * Combines this set with the specified set to produce their cartesian
   * product.
   */
  def zip[B1 >: B, C](
    that: ZSet[C, B1]
  )(implicit ev1: Commutative[Sum[B1]], ev2: Commutative[Prod[B1]]): ZSet[(A, C), B1] =
    zipWith(that)((_, _))

  /**
   * Combines this set with the specified set to produce their cartesian
   * product, combining pair of elements using the specified function `f`.
   */
  def zipWith[B1 >: B, C, D](
    that: ZSet[C, B1]
  )(f: (A, C) => D)(implicit ev1: Commutative[Sum[B1]], ev2: Commutative[Prod[B1]]): ZSet[D, B1] =
    self.flatMap(a => that.map(c => f(a, c)))
}

object ZSet {

  /**
   * Constructs a set from the specified `Map`.
   */
  def apply[A, B](map: Map[A, B]): ZSet[A, B] =
    new ZSet(map)

  /**
   * Constructs a set with the specified elements.
   */
  def apply[A](as: A*): ZSet[A, Int] =
    fromIterable(as)

  /**
   * The empty set.
   */
  val empty: ZSet[Nothing, Nothing] =
    ZSet(Map.empty)

  /**
   * Constructs a set from the specified `Iterable`. The measure of how many
   * times a value occurs in the set will be an integer representing how many
   * times the value occurred in the specified `Iterable`.
   */
  def fromIterable[A](iterable: Iterable[A]): ZSet[A, Int] =
    ZSet(iterable.foldLeft(Map.empty[A, Int])((map, a) => map + (a -> map.get(a).fold(1)(_ + 1))))

  /**
   * Constructs a set from the specified `Set`. The measure of how many times
   * a value occurs in the set will be a boolean representing whether a value
   * occurs at all.
   */
  def fromSet[A](set: Set[A]): ZSet[A, Boolean] =
    ZSet(set.foldLeft(Map.empty[A, Boolean])((map, a) => map + (a -> true)))

  /**
   * Constructs a set from the specified `Map`. The values will be the keys in
   * the `Map` and the measure of how many times a value occurs will be the
   */
  def fromMap[A, B](map: Map[A, B]): ZSet[A, B] =
    ZSet(map)

  /**
   * Derives a `Commutative[ZSet[A, B]]` given a `Commutative[B]`.
   */
  implicit def ZSetCommutative[A, B: Commutative]: Commutative[ZSet[A, B]] =
    new Commutative[ZSet[A, B]] {
      def combine(left: => ZSet[A, B], right: => ZSet[A, B]): ZSet[A, B] =
        ZSet {
          right.map.foldLeft(left.map) {
            case (map, (a, b1)) =>
              map.get(a) match {
                case Some(b) => map + (a -> (b <> b1))
                case None    => map + (a -> b1)
              }
          }
        }
    }

  /**
   * Derives an `Equal[ZSet[A, B]]` given an `Equal[B]`. Due to the
   * limitations of Scala's `Map`, this uses object equality on the keys.
   */
  implicit def ZSetEqual[A, B: Equal]: Equal[ZSet[A, B]] =
    Equal[Map[A, B]].contramap(_.map)

  /**
   * The `EqualF` instance for `ZSet`.
   */
  implicit def ZSetDeriveEqual[B: Equal]: DeriveEqual[({ type lambda[+x] = ZSet[x, B] })#lambda] =
    new DeriveEqual[({ type lambda[+x] = ZSet[x, B] })#lambda] {
      def derive[A: Equal]: Equal[ZSet[A, B]] =
        ZSetEqual
    }

  /**
   * The `Covariant` instance for `ZSet`.
   */
  implicit def ZSetCovariant[B](
    implicit ev: Commutative[Sum[B]]
  ): Covariant[({ type lambda[+x] = ZSet[x, B] })#lambda] =
    new Covariant[({ type lambda[+x] = ZSet[x, B] })#lambda] {
      def map[A, C](f: A => C): ZSet[A, B] => ZSet[C, B] =
        _.map(f)
    }

  /**
   * The `IdentityFlatten` instance for `ZSet`.
   */
  implicit def ZSetIdentityFlatten[B](
    implicit ev1: Commutative[Sum[B]],
    ev2: Commutative[Prod[B]]
  ): IdentityFlatten[({ type lambda[+x] = ZSet[x, B] })#lambda] =
    new IdentityFlatten[({ type lambda[+x] = ZSet[x, B] })#lambda] {
      def flatten[A](ffa: ZSet[ZSet[A, B], B]): ZSet[A, B] =
        ffa.flatMap(identity)
      def any: ZSet[Any, B] = ???

    }

  /**
   * Derives a `Hash[ZSet[A, B]]` given a `Hash[B]`. Due to the
   * limitations of Scala's `Map`, this uses object equality on the keys.
   */
  implicit def ZSetHash[A, B: Hash]: Hash[ZSet[A, B]] =
    Hash[Map[A, B]].contramap(_.map)
}
