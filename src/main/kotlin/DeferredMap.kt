typealias D<T> = () -> T

class DeferredMap<K, V>(source: Map<K, D<V>> = emptyMap(), private val insane: Boolean = false) : MutableMap<K, V> {
    private val backing = HashMap(source)
    override val keys: MutableSet<K> get() = backing.keys
    override val size: Int get() = backing.size
    override val values: MutableCollection<V> get() = backing.keys.map { this[it]!! }.toMutableList()
    override fun clear() = backing.clear()
    override fun containsKey(key: K): Boolean = key in keys
    override fun containsValue(value: V): Boolean = value in values
    override fun get(key: K): V? {
        val func = backing[key] ?: return null
        val retVal = func()
        backing[key] = { retVal }
        return retVal
    }

    override fun isEmpty(): Boolean = backing.isEmpty()
    override fun putAll(from: Map<out K, V>) {
        from.forEach { (k, v) ->
            this[k] = v
        }
    }

    override fun remove(key: K): V? = backing.remove(key)?.invoke()
    override fun put(key: K, value: V): V? {
        return if (insane) {
            backing[key] = { value }
            null
        } else {
            backing.put(key) { value }?.invoke()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = backing.entries
            .map { (k) -> ME(k, this[k]!!) as MutableMap.MutableEntry<K, V> }
            .toMutableSet()
}

data class ME<K, V>(override val key: K, override val value: V) : MutableMap.MutableEntry<K, V> {
    override fun setValue(newValue: V): V = value
}
