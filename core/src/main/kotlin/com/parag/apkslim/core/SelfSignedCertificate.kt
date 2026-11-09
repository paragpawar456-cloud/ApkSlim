package com.parag.apkslim.core

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Creates an RSA key pair and a self-signed X.509 v3 certificate using only the JDK
 * (no BouncyCastle), so the key can be stored in a PKCS#12 file and backed up by the user.
 */
object SelfSignedCertificate {

    fun generate(commonName: String, organization: String, years: Int = 30, keySize: Int = 2048): Pair<KeyPair, X509Certificate> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(keySize, SecureRandom()) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 3600 * 1000)
        val notAfter = Date(now + years * 365L * 24 * 3600 * 1000)
        val serial = BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE)

        val sigAlg = seq(oid(SHA256_WITH_RSA), byteArrayOf(0x05, 0x00))
        val name = seq(
            set(seq(oid(OID_O), utf8(organization))),
            set(seq(oid(OID_CN), utf8(commonName))),
        )
        val tbs = seq(
            tag(0xA0, integer(BigInteger.valueOf(2))), // v3
            integer(serial),
            sigAlg,
            name,
            seq(time(notBefore), time(notAfter)),
            name,
            kp.public.encoded, // SubjectPublicKeyInfo, already DER
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(kp.private); update(tbs); sign()
        }
        val certDer = seq(tbs, sigAlg, bitString(signature))
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(certDer.inputStream()) as X509Certificate
        cert.verify(kp.public)
        return kp to cert
    }

    // ---- minimal DER encoder ----
    private const val SHA256_WITH_RSA = "1.2.840.113549.1.1.11"
    private const val OID_CN = "2.5.4.3"
    private const val OID_O = "2.5.4.10"

    private fun len(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    }

    private fun tag(t: Int, vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
        return ByteArrayOutputStream().apply { write(t); write(len(body.size)); write(body) }.toByteArray()
    }

    private fun seq(vararg parts: ByteArray) = tag(0x30, *parts)
    private fun set(vararg parts: ByteArray) = tag(0x31, *parts)
    private fun integer(v: BigInteger) = tag(0x02, v.toByteArray())
    private fun utf8(s: String) = tag(0x0C, s.toByteArray(Charsets.UTF_8))
    private fun bitString(b: ByteArray) = tag(0x03, byteArrayOf(0), b)

    private fun time(d: Date): ByteArray {
        val year = SimpleDateFormat("yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(d).toInt()
        return if (year < 2050) {
            tag(0x17, fmt("yyMMddHHmmss'Z'", d).toByteArray(Charsets.US_ASCII))
        } else {
            tag(0x18, fmt("yyyyMMddHHmmss'Z'", d).toByteArray(Charsets.US_ASCII))
        }
    }

    private fun fmt(p: String, d: Date) = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(d)

    private fun oid(dotted: String): ByteArray {
        val parts = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        for (p in parts.drop(2)) {
            val stack = ArrayList<Int>()
            var v = p
            stack.add((v and 0x7F).toInt())
            v = v shr 7
            while (v > 0) { stack.add(((v and 0x7F) or 0x80).toInt()); v = v shr 7 }
            stack.reversed().forEach { out.write(it) }
        }
        return tag(0x06, out.toByteArray())
    }
}
