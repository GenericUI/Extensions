package com.nexustools.genericui.ext.box2d

/**
 * Listener interface for receiving 2D collision lifecycle callbacks from [World2D] (`b2ContactListener`).
 */
interface ContactListener2D {
    fun beginContact(contact: Contact2D) {}
    fun endContact(contact: Contact2D) {}
    fun preSolve(contact: Contact2D, oldManifold: Manifold2D) {}
    fun postSolve(contact: Contact2D, impulse: ContactImpulse2D) {}
}
