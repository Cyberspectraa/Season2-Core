# Season 2 Core 0.3.3-alpha.3

Runtime compatibility fix for directional postal block placement.

Minecraft 1.20.1 StateHolder<O, S> uses an unbounded S type parameter.
Therefore StateHolder#m_61124_(Property, Comparable) erases to Object.

0.3.2-alpha.3 was compiled against a temporary stub that incorrectly bounded
S to StateHolder, producing the wrong JVM return descriptor and a
NoSuchMethodError on block placement.

0.3.3-alpha.3 recompiles only PostalBlock.class with the production-compatible
Object return descriptor. Directional placement and all alpha.3 postal features
remain unchanged.
