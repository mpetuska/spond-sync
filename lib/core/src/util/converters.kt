package core.util

fun <T> Triple<T, T, T>.toList() = listOf(first, second, third)

fun <T> List<T>.toTriple(): Triple<T, T, T> {
  require(size == 3) { "List needs size=3 to be converted to Triple, was size=$size" }
  return Triple(get(0), get(1), get(2))
}

operator fun <T> Triple<T, T, T>.plus(other: Triple<T, T, T>) = toList() + other.toList()
