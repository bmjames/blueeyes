package blueeyes.core

package object data{

  type ByteChunk = Chunk[Array[Byte]]

  type <~>[A, B] = Bijection[A, B]

}
