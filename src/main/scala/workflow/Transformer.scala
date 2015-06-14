package workflow

import org.apache.spark.rdd.RDD
import pipelines.Logging

import scala.reflect.ClassTag

trait SingleTransformerPipeline[A, B, T <: ExposableTransformer[A, B, T]] extends Pipeline[A, B]

private[workflow] class ConcreteSingleTransformerPipeline[A, B, T <: ExposableTransformer[A, B, T]] (
  nodes: Seq[Node],
  dataDeps: Seq[Seq[Int]],
  fitDeps: Seq[Seq[Int]],
  sink: Int) extends ConcretePipeline[A, B](nodes, dataDeps, fitDeps, sink) with SingleTransformerPipeline[A, B, T]

abstract class ExposableTransformer[A, B : ClassTag, THIS <: ExposableTransformer[A, B, THIS]] extends TransformerNode[B] with SingleTransformerPipeline[A, B, THIS] {
  override val nodes: Seq[Node] = Seq(this)
  override val dataDeps: Seq[Seq[Int]] = Seq(Seq(Pipeline.SOURCE))
  override val fitDeps: Seq[Seq[Int]] = Seq(Seq())
  override val sink: Int = 0

  /**
   * Apply this Transformer to an RDD of input items
   * @param in The bulk RDD input to pass into this transformer
   * @return The bulk RDD output for the given input
   */
  def apply(in: RDD[A]): RDD[B] = in.map(apply)

  /**
   * Apply this Transformer to a single input item
   * @param in  The input item to pass into this transformer
   * @return  The output value
   */
  def apply(in: A): B

  def transform(dataDependencies: Seq[_], fitDependencies: Seq[TransformerNode[_]]): B = apply(dataDependencies.head.asInstanceOf[A])

  def transformRDD(dataDependencies: Seq[RDD[_]], fitDependencies: Seq[TransformerNode[_]]): RDD[B] = apply(dataDependencies.head.asInstanceOf[RDD[A]])
}

/**
 * Transformers are functions that may be applied both to single input items and to RDDs of input items.
 * They may be chained together, along with [[Estimator]]s and [[LabelEstimator]]s, to produce complex
 * pipelines.
 *
 * @tparam A input item type the transformer takes
 * @tparam B output item type the transformer produces
 */
abstract class Transformer[A, B : ClassTag] extends ExposableTransformer[A, B, Transformer[A, B]]

object Transformer {
  /**
   * This constructor takes a function and returns a Transformer that maps it over the input RDD
   * @param f The function to apply to every item in the RDD being transformed
   * @tparam I input type of the transformer
   * @tparam O output type of the transformer
   * @return Transformer that applies the given function to all items in the RDD
   */
  def apply[I, O : ClassTag](f: I => O): Transformer[I, O] = new Transformer[I, O] {
    override def apply(in: RDD[I]): RDD[O] = in.map(f)
    override def apply(in: I): O = f(in)
  }
}
