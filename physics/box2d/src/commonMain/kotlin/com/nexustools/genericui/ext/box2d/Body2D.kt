package com.nexustools.genericui.ext.box2d

/**
 * 2D rigid body (`b2Body`) simulated inside a [World2D].
 */
class Body2D(
    def: BodyDef2D,
    val world: World2D
) {
    var type: BodyType2D = def.type
    val transform: Transform2D = Transform2D(def.position.clone(), Rot(def.angle))
    val localCenter: Vec2 = Vec2()
    val worldCenter: Vec2 = def.position.clone()

    val linearVelocity: Vec2 = def.linearVelocity.clone()
    var angularVelocity: Float = def.angularVelocity
    var linearDamping: Float = def.linearDamping
    var angularDamping: Float = def.angularDamping
    var gravityScale: Float = def.gravityScale

    var isBullet: Boolean = def.bullet
    var isSleepingAllowed: Boolean = def.allowSleep
    var isAwake: Boolean = def.awake
    var isEnabled: Boolean = def.enabled
    var isFixedRotation: Boolean = def.fixedRotation
    var userData: Any? = def.userData

    var mass: Float = 0f
        private set
    var invMass: Float = 0f
        private set
    var inertia: Float = 0f
        private set
    var invInertia: Float = 0f
        private set

    internal val force: Vec2 = Vec2()
    internal var torque: Float = 0f

    private val _fixtures = mutableListOf<Fixture2D>()
    val fixtures: List<Fixture2D>
        get() = _fixtures

    val position: Vec2
        get() = transform.p

    val angle: Float
        get() = transform.q.getAngle()

    fun setTransform(position: Vec2, angleRadians: Float) {
        transform.set(position, angleRadians)
        worldCenter.copy(transform.mul(localCenter))
    }

    fun createFixture(def: FixtureDef2D): Fixture2D {
        val fixture = Fixture2D(this, def)
        _fixtures.add(fixture)
        if (fixture.density > 0f) {
            resetMassData()
        }
        return fixture
    }

    fun createFixture(shape: Shape2D, density: Float = 0f): Fixture2D =
        createFixture(FixtureDef2D(shape = shape, density = density))

    fun destroyFixture(fixture: Fixture2D) {
        if (_fixtures.remove(fixture)) {
            resetMassData()
        }
    }

    fun resetMassData() {
        mass = 0f
        invMass = 0f
        inertia = 0f
        invInertia = 0f
        localCenter.setZero()

        if (type == BodyType2D.STATIC || type == BodyType2D.KINEMATIC) {
            worldCenter.copy(transform.p)
            return
        }

        val tempCenter = Vec2()
        for (f in _fixtures) {
            if (f.density <= 0f) continue
            val md = f.shape.computeMass(f.density)
            mass += md.mass
            tempCenter.addScaledLocal(md.center, md.mass)
            inertia += md.inertia
        }

        if (mass > 0f) {
            invMass = 1f / mass
            localCenter.copy(tempCenter * invMass)
        }

        if (inertia > 0f && !isFixedRotation) {
            inertia -= mass * localCenter.lengthSquared()
            inertia = inertia.coerceAtLeast(1e-4f)
            invInertia = 1f / inertia
        } else {
            inertia = 0f
            invInertia = 0f
        }

        worldCenter.copy(transform.mul(localCenter))
    }

    fun applyForce(forceVec: Vec2, point: Vec2, wake: Boolean = true) {
        if (type != BodyType2D.DYNAMIC) return
        if (wake && !isAwake) isAwake = true
        if (isAwake) {
            force.addLocal(forceVec)
            torque += Vec2.cross(point - worldCenter, forceVec)
        }
    }

    fun applyForceToCenter(forceVec: Vec2, wake: Boolean = true) {
        if (type != BodyType2D.DYNAMIC) return
        if (wake && !isAwake) isAwake = true
        if (isAwake) {
            force.addLocal(forceVec)
        }
    }

    fun applyTorque(torqueAmount: Float, wake: Boolean = true) {
        if (type != BodyType2D.DYNAMIC) return
        if (wake && !isAwake) isAwake = true
        if (isAwake) {
            torque += torqueAmount
        }
    }

    fun applyLinearImpulse(impulse: Vec2, point: Vec2, wake: Boolean = true) {
        if (type != BodyType2D.DYNAMIC) return
        if (wake && !isAwake) isAwake = true
        if (isAwake) {
            linearVelocity.addScaledLocal(impulse, invMass)
            angularVelocity += invInertia * Vec2.cross(point - worldCenter, impulse)
        }
    }

    fun applyLinearImpulseToCenter(impulse: Vec2, wake: Boolean = true) {
        if (type != BodyType2D.DYNAMIC) return
        if (wake && !isAwake) isAwake = true
        if (isAwake) {
            linearVelocity.addScaledLocal(impulse, invMass)
        }
    }

    fun applyAngularImpulse(impulse: Float, wake: Boolean = true) {
        if (type != BodyType2D.DYNAMIC) return
        if (wake && !isAwake) isAwake = true
        if (isAwake) {
            angularVelocity += invInertia * impulse
        }
    }

    fun getWorldPoint(localPoint: Vec2): Vec2 = transform.mul(localPoint)
    fun getWorldVector(localVector: Vec2): Vec2 = transform.q.mulVec(localVector)
    fun getLocalPoint(worldPoint: Vec2): Vec2 = transform.mulTrans(worldPoint)
    fun getLocalVector(worldVector: Vec2): Vec2 = transform.q.mulTransVec(worldVector)
}
