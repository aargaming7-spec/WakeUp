package com.wakeup

enum class FaceMode { NONE, ANY, SMILE }

/** Satu challenge = gabungan langkah: jalan -> shake -> putar (gyroscope) -> kamera (objek/wajah). */
data class Challenge(val id: Int, val level: Int, val title: String, val obj: String? = null,
                     val face: FaceMode = FaceMode.NONE, val shakes: Int = 0, val steps: Int = 0,
                     val rotateDeg: Int = 0)

val LEVELS = listOf("Easy", "Medium", "Hard")
val CHALLENGES = listOf(
    Challenge(1, 0, "Tunjukkan botol", obj = "Bottle"),
    Challenge(2, 0, "Tunjukkan wajah", face = FaceMode.ANY),
    Challenge(3, 0, "Shake 10 kali", shakes = 10),
    Challenge(9, 0, "Putar HP 180°", rotateDeg = 150),
    Challenge(4, 1, "Tersenyum ke kamera", face = FaceMode.SMILE),
    Challenge(5, 1, "Tunjukkan botol + tersenyum", obj = "Bottle", face = FaceMode.SMILE),
    Challenge(6, 1, "Jalan 10 langkah", steps = 10),
    Challenge(10, 1, "Putar HP 180° + tunjukkan wajah", rotateDeg = 150, face = FaceMode.ANY),
    Challenge(7, 2, "Jalan 10 langkah + tunjukkan sepatu", obj = "Shoe", steps = 10),
    Challenge(8, 2, "Tunjukkan buku + tersenyum + shake 5 kali", obj = "Book", face = FaceMode.SMILE, shakes = 5),
)
fun poolFor(level: Int) = CHALLENGES.filter { it.level == level }
fun randomChallenge(level: Int) = poolFor(level).random()
fun challengeOf(id: Int) = CHALLENGES.firstOrNull { it.id == id } ?: CHALLENGES[0]
