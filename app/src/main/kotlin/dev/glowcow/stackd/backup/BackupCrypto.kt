package dev.glowcow.stackd.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The password does not open the file. */
class WrongPasswordException : IOException("wrong password")

/**
 * A backup under a password: the header, then chunks of AES-256-GCM. Each chunk has its own nonce and
 * says whether it is the last one, so a file cut short or reordered does not decrypt.
 */
object BackupCrypto {
    val MAGIC = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 'A'.code.toByte(), 'C'.code.toByte(), 'K'.code.toByte(), 'D'.code.toByte(), 1, '\n'.code.toByte())

    private const val SALT = 16
    private const val PREFIX = 4
    private const val CHUNK = 64 * 1024
    private const val TAG = 16
    private const val ITERATIONS = 200_000
    private const val LAST = Int.MIN_VALUE

    fun isEncrypted(head: ByteArray) = head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    private fun cipher(mode: Int, key: SecretKeySpec, prefix: ByteArray, index: Long, last: Boolean): Cipher {
        val nonce = ByteBuffer.allocate(PREFIX + 8).put(prefix).putLong(index).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG * 8, nonce))
            updateAAD(byteArrayOf(if (last) 1 else 0))
        }
    }

    /** What is written to the result goes to [out] encrypted; closing it ends the file and closes [out]. */
    fun encrypt(out: OutputStream, password: CharArray): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT).also(random::nextBytes)
        val prefix = ByteArray(PREFIX).also(random::nextBytes)
        val key = key(password, salt)
        val data = DataOutputStream(out)
        data.write(MAGIC)
        data.write(salt)
        data.write(prefix)
        return object : OutputStream() {
            private val buffer = ByteArray(CHUNK)
            private var filled = 0
            private var index = 0L
            private var closed = false

            private fun flushChunk(last: Boolean) {
                val sealed = cipher(Cipher.ENCRYPT_MODE, key, prefix, index++, last).doFinal(buffer, 0, filled)
                data.writeInt(if (last) sealed.size or LAST else sealed.size)
                data.write(sealed)
                filled = 0
            }

            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                var at = off
                var left = len
                while (left > 0) {
                    if (filled == CHUNK) flushChunk(last = false)
                    val n = minOf(left, CHUNK - filled)
                    b.copyInto(buffer, filled, at, at + n)
                    filled += n
                    at += n
                    left -= n
                }
            }

            override fun close() {
                if (closed) return
                closed = true
                flushChunk(last = true)
                data.close()
            }
        }
    }

    /** The plain content of an encrypted backup; [input] starts at the header. */
    fun decrypt(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also(data::readFully)
        if (!magic.contentEquals(MAGIC)) throw IOException("not an encrypted backup")
        val salt = ByteArray(SALT).also(data::readFully)
        val prefix = ByteArray(PREFIX).also(data::readFully)
        val key = key(password, salt)
        return object : InputStream() {
            private var chunk = ByteArray(0)
            private var at = 0
            private var index = 0L
            private var done = false

            private fun fill(): Boolean {
                while (at == chunk.size) {
                    if (done) return false
                    val header = try {
                        data.readInt()
                    } catch (e: EOFException) {
                        throw IOException("the backup is cut short", e)
                    }
                    val last = header and LAST != 0
                    val size = header and LAST.inv()
                    if (size < TAG || size > CHUNK + TAG) throw IOException("bad chunk")
                    val sealed = ByteArray(size).also(data::readFully)
                    chunk = try {
                        cipher(Cipher.DECRYPT_MODE, key, prefix, index, last).doFinal(sealed)
                    } catch (e: GeneralSecurityException) {
                        // A wrong key fails on the very first chunk; later it is damage.
                        if (index == 0L) throw WrongPasswordException() else throw IOException("the backup is damaged", e)
                    }
                    index++
                    at = 0
                    done = last
                }
                return true
            }

            override fun read(): Int = if (fill()) chunk[at++].toInt() and 0xFF else -1

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                if (!fill()) return -1
                val n = minOf(len, chunk.size - at)
                chunk.copyInto(b, off, at, at + n)
                at += n
                return n
            }

            override fun close() = data.close()
        }
    }
}
