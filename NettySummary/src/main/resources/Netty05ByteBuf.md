##  第  5 章 Netty的组件和设计 

### 5.1 ByteBuf 的 API



Netty 的数据处理 API 通过两个组件暴露——abstract class ByteBuf 和 interface 

ByteBufHolder。

下面是一些 ByteBuf API 的优点：

- 它可以被用户自定义的缓冲区类型扩展；
- 通过内置的复合缓冲区类型实现了透明的零拷贝；
- 容量可以按需增长（类似于 JDK 的 StringBuilder）；
- 在读和写这两种模式之间切换不需要调用 ByteBuffer 的 flip()方法；
- 读和写使用了不同的索引； 
- 支持方法的链式调用； 
- 支持引用计数
- 支持池化

### 5.2ByteBuf 类——Netty 的数据容器

#### 5.2.1 工作原理

ByteBuf 维护了两个不同的索引：一个用于读取，一个用于写入。当你从 ByteBuf 读取时，它的 readerIndex 将会被递增已经被读取的字节数。同样地，当你写入 ByteBuf 时，它的writerIndex 也会被递增。图 5-1 展示了一个空 ByteBuf 的布局结构和状态。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216143345953.png)

如果打算读取字节直到 readerIndex 达到 和 writerIndex 同样的值时会发生什么。在那时，你将会到达“可以读取的”数据的末尾。就如同试图读取超出数组末尾的数据一样，试图读取超出该点的数据将会触发一个 IndexOutOfBoundsException。 

#### 5.2.2 ByteBuf 的使用模式

**1、堆缓冲区**

最常用的 ByteBuf 模式是将数据存储在 JVM 的堆空间中。这种模式被称为支撑数组（backing array），它能在没有使用池化的情况下提供快速的分配和释放。这种方式，如代码清单5-1 所示，非常适合于有遗留的数据需要处理的情况。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216143737978.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)



**2．直接缓冲区**

直接缓冲区是另外一种 ByteBuf 模式。我们期望用于对象创建的内存分配永远都来自于堆中，但这并不是必须的——NIO 在 JDK 1.4 中引入的 ByteBuffer 类允许 JVM 实现通过本地调用来分配内存。这主要是为了避免在每次调用本地 I/O 操作之前（或者之后）将缓冲区的内容复制到一个中间缓冲区（或者从中间缓冲区把内容复制到缓冲区）。

ByteBuffer的Javadoc①明确指出：“直接缓冲区的内容将驻留在常规的会被垃圾回收的堆之外。”这也就解释了为何直接缓冲区对于网络数据传输是理想的选择。如果你的数据包含在一个在堆上分配的缓冲区中，那么事实上，在通过套接字发送它之前，JVM将会在内部把你的缓冲区复制到一个直接缓冲区中。

直接缓冲区的主要缺点是，相对于基于堆的缓冲区，它们的分配和释放都较为昂贵。如果你正在处理遗留代码，你也可能会遇到另外一个缺点：因为数据不是在堆上，所以你不得不进行一次复制，如代码清单 5-2 所示。

显然，与使用支撑数组相比，这涉及的工作更多。因此，如果事先知道容器中的数据将会被作为数组来访问，你可能更愿意使用堆内存。

![在这里插入图片描述](https://img-blog.csdnimg.cn/2020121614464753.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)



**3．复合缓冲区**

第三种也是最后一种模式使用的是复合缓冲区，它为多个 ByteBuf 提供一个聚合视图。在这里你可以根据需要添加或者删除 ByteBuf 实例，这是一个 JDK 的 ByteBuffer 实现完全缺失的特性。

Netty 通过一个 ByteBuf 子类——CompositeByteBuf——实现了这个模式，它提供了一个将多个缓冲区表示为单个合并缓冲区的虚拟表示。

>**警告** CompositeByteBuf 中的 ByteBuf 实例可能同时包含直接内存分配和非直接内存分配。如果其中只有一个实例，那么对 CompositeByteBuf 上的 hasArray()方法的调用将返回该组件上的 hasArray()方法的值；否则它将返回 false

为了举例说明，让我们考虑一下一个由两部分——头部和主体——组成的将通过 HTTP 协议传输的消息。这两部分由应用程序的不同模块产生，将会在消息被发送的时候组装。该应用程序可以选择为多个消息重用相同的消息主体。当这种情况发生时，对于每个消息都将会创建一个新的头部。

因为我们不想为每个消息都重新分配这两个缓冲区，所以使用 CompositeByteBuf 是一个完美的选择。它在消除了没必要的复制的同时，暴露了通用的 ByteBuf API。图 5-2 展示了生成的消息布局。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216144940748.png)



代码清单 5-3 展示了如何通过使用 JDK 的 ByteBuffer 来实现这一需求。创建了一个包含两个 ByteBuffer 的数组用来保存这些消息组件，同时创建了第三个 ByteBuffer 用来保存所有这些数据的副本。

![在这里插入图片描述](https://img-blog.csdnimg.cn/2020121614503633.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

分配和复制操作，以及伴随着对数组管理的需要，使得这个版本的实现效率低下而且笨拙。代码清单 5-4 展示了一个使用了 CompositeByteBuf 的版本。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216145108532.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

CompositeByteBuf 可能不支持访问其支撑数组，因此访问CompositeByteBuf中的数据类似于（访问）直接缓冲区的模式，如代码清单 5-5 所示。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216145203946.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

需要注意的是，Netty使用了CompositeByteBuf来优化套接字的I/O操作，尽可能地消除了由JDK的缓冲区实现所导致的性能以及内存使用率的惩罚。这种优化发生在Netty的核心代码中，因此不会被暴露出来，但是你应该知道它所带来的影响。

### 5.3 字节级操作

**5.3.1 随机访问索引**

如同在普通的 Java 字节数组中一样，ByteBuf 的索引是从零开始的：第一个字节的索引是0，最后一个字节的索引总是 capacity() - 1。代码清单 5-6 表明，对存储机制的封装使得遍历 ByteBuf 的内容非常简单。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216145536830.png)



需要注意的是，使用那些需要一个索引值参数的方法（的其中）之一来访问数据既不会改变readerIndex 也不会改变 writerIndex。如果有需要，也可以通过调用 readerIndex(index)或者 writerIndex(index)来手动移动这两者。

**5.3.2 顺序访问索引**

虽然 ByteBuf 同时具有读索引和写索引，但是 JDK 的 ByteBuffer 却只有一个索引，这也就是为什么必须调用 flip()方法来在读模式和写模式之间进行切换的原因。图 5-3 展示了ByteBuf 是如何被它的两个索引划分成 3 个区域的。

![在这里插入图片描述](https://img-blog.csdnimg.cn/202012161456365.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)



**5.3.3 可丢弃字节**

在图 5-3 中标记为可丢弃字节的分段包含了已经被读过的字节。通过调用 discardReadBytes()方法，可以丢弃它们并回收空间。这个分段的初始大小为 0，存储在 readerIndex 中，会随着 read 操作的执行而增加（get*操作不会移动 readerIndex）。

图 5-4 展示了图 5-3 中所展示的缓冲区上调用discardReadBytes()方法后的结果。可以看到，可丢弃字节分段中的空间已经变为可写的了。注意，在调用discardReadBytes()之后，对可写分段的内容并没有任何的保证。 

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216145813816.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

虽然你可能会倾向于频繁地调用 discardReadBytes()方法以确保可写分段的最大化，但是请注意，这将极有可能会导致内存复制，因为可读字节（图中标记为 CONTENT 的部分）必须被移动到缓冲区的开始位置。我们建议只在有真正需要的时候才这样做，例如，当内存非常宝贵的时候。

**5.3.4 可读字节**

ByteBuf 的可读字节分段存储了实际数据。新分配的、包装的或者复制的缓冲区的默认的readerIndex 值为 0。任何名称以 read 或者 skip 开头的操作都将检索或者跳过位于当前readerIndex 的数据，并且将它增加已读字节数。

如果被调用的方法需要一个 ByteBuf 参数作为写入的目标，并且没有指定目标索引参数，那么该目标缓冲区的 writerIndex 也将被增加，例如：

```java
readBytes(ByteBuf dest);
```

如果尝试在缓冲区的可读字节数已经耗尽时从中读取数据，那么将会引发一个 IndexOutOfBoundsException。

代码清单 5-7 展示了如何读取所有可以读的字节

```java
ByteBuf buffer = ...;
while (buffer.isReadable()) {
    System.out.println(buffer.readByte());
}
```

**5.3.5 可写字节**

可写字节分段是指一个拥有未定义内容的、写入就绪的内存区域。新分配的缓冲区的writerIndex 的默认值为 0。任何名称以 write 开头的操作都将从当前的 writerIndex 处开始写数据，并将它增加已经写入的字节数。如果写操作的目标也是 ByteBuf，并且没有指定源索引的值，则源缓冲区的 readerIndex 也同样会被增加相同的大小。这个调用如下所示：

```java
writeBytes(ByteBuf dest);
```

如果尝试往目标写入超过目标容量的数据，将会引发一个IndexOutOfBoundException

代码清单5-8 是一个用随机整数值填充缓冲区，直到它空间不足为止的例子。writeableBytes()方法在这里被用来确定该缓冲区中是否还有足够的空间。

```java
ByteBuf buffer = ...;
while (buffer.writableBytes() >= 4) {
	buffer.writeInt(random.nextInt());
}
```



**5.3.6 索引管理**

JDK 的 InputStream 定义了 mark(int readlimit)和 reset()方法，这些方法分别被用来将流中的当前位置标记为指定的值，以及将流重置到该位置。

同样，可以通过调用 markReaderIndex()、markWriterIndex()、resetWriterIndex()和 resetReaderIndex()来标记和重置 ByteBuf 的 readerIndex 和 writerIndex。这些和InputStream 上的调用类似，只是没有 readlimit 参数来指定标记什么时候失效。

也可以通过调用 readerIndex(int)或者 writerIndex(int)来将索引移动到指定位置。试图将任何一个索引设置到一个无效的位置都将导致一个 IndexOutOfBoundsException。

可以通过调用 clear()方法来将 readerIndex 和 writerIndex 都设置为 0。注意，这并不会清除内存中的内容。图 5-5（重复上面的图 5-3）展示了它是如何工作的。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216150439452.png)

和之前一样，ByteBuf 包含 3 个分段。图 5-6 展示了在 clear()方法被调用之后ByteBuf的状态。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216150600342.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

调用 clear()比调用 discardReadBytes()轻量得多，因为它将只是重置索引而不会复 制任何的内存。

**5.3.7 查找操作**

在ByteBuf中有多种可以用来确定指定值的索引的方法。最简单的是使用indexOf()方法。较复杂的查找可以通过那些需要一个ByteBufProcessor作为参数的方法达成。这个接口只定义了一个方法：

```java
boolean process(byte value)
```

它将检查输入值是否是正在查找的值。



ByteBufProcessor针对一些常见的值定义了许多便利的方法。假设你的应用程序需要和所谓的包含有以NULL结尾的内容的Flash套接字集成。调用

```java
forEachByte(ByteBufProcessor.FIND_NUL)
```

将简单高效地消费该 Flash 数据，因为在处理期间只会执行较少的边界检查。

代码清单 5-9 展示了一个查找回车符（\r）的例子。

```java
ByteBuf buffer = ...;
int index = buffer.forEachByte(ByteBufProcessor.FIND_CR);
```



**5.3.8 派生缓冲区**

派生缓冲区为 ByteBuf 提供了以专门的方式来呈现其内容的视图。这类视图是通过以下方

法被创建的： 

 duplicate()； 

 slice()；

 slice(int, int)； 

 Unpooled.unmodifiableBuffer(…)； 

 order(ByteOrder)； 

 readSlice(int)

每个这些方法都将返回一个新的 ByteBuf 实例，它具有自己的读索引、写索引和标记索引。其内部存储和 JDK 的 ByteBuffer 一样也是共享的。这使得派生缓冲区的创建成本是很低廉的，但是这也意味着，如果你修改了它的内容，也同时修改了其对应的源实例，所以要小心。

> **ByteBuf** 复制 如果需要一个现有缓冲区的真实副本，请使用 copy()或者 copy(int, int)方法。不同于派生缓冲区，由这个调用所返回的 ByteBuf 拥有独立的数据副本。

代码清单 5-10 展示了如何使用 slice(int, int)方法来操作 ByteBuf 的一个分段。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216151052764.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

现在，让我们看看 ByteBuf 的分段的副本和切片有何区别，如代码清单 5-11 所示。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216151120919.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

除了修改原始 ByteBuf 的切片或者副本的效果以外，这两种场景是相同的。只要有可能，使用 slice()方法来避免复制内存的开销

**5.3.9 读/写操作**

正如我们所提到过的，有两种类别的读/写操作：

 get()和 set()操作，从给定的索引开始，并且保持索引不变； 

 read()和 write()操作，从给定的索引开始，并且会根据已经访问过的字节数对索 引进行调整。



### 5.4 ByteBufHolder 接口

我们经常发现，除了实际的数据负载之外，我们还需要存储各种属性值。HTTP 响应便是一个很好的例子，除了表示为字节的内容，还包括状态码、cookie 等。

为了处理这种常见的用例，Netty 提供了 ByteBufHolder。ByteBufHolder 也为 Netty 的高级特性提供了支持，如缓冲区池化，其中可以从池中借用 ByteBuf，并且在需要时自动释放。

ByteBufHolder 只有几种用于访问底层数据和引用计数的方法。表 5-6 列出了它们（这里不包括它继承自 ReferenceCounted 的那些方法）

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216151509786.png)



如果想要实现一个将其有效负载存储在 ByteBuf 中的消息对象，那么 ByteBufHolder 将是个不错的选择。

### 5.5 ByteBuf 分配

**5.5.1 按需分配：ByteBufAllocator 接口**

为了降低分配和释放内存的开销，Netty 通过 interface ByteBufAllocator 实现了（ByteBuf 的）池化，它可以用来分配我们所描述过的任意类型的 ByteBuf 实例。使用池化是特定于应用程序的决定，其并不会以任何方式改变 ByteBuf API（的语义）。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216151713469.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)



可以通过 Channel（每个都可以有一个不同的 ByteBufAllocator 实例）或者绑定到ChannelHandler 的 ChannelHandlerContext 获取一个到 ByteBufAllocator 的引用。

代码清单 5-14 说明了这两种方法

![在这里插入图片描述](https://img-blog.csdnimg.cn/2020121615175473.png)

Netty提供了两种ByteBufAllocator的实现：PooledByteBufAllocator和UnpooledByteBufAllocator。前者池化了ByteBuf的实例以提高性能并最大限度地减少内存碎片。此实现使用了一种称为jemalloc②的已被大量现代操作系统所采用的高效方法来分配内存。后者的实现不池化ByteBuf实例，并且在每次它被调用时都会返回一个新的实例。

虽然Netty默认使用了PooledByteBufAllocator，但这可以很容易地通过ChannelConfig API或者在引导你的应用程序时指定一个不同的分配器来更改。更多的细节可在第8 章中找到。

**5.5.2 Unpooled 缓冲区**

可能某些情况下，你未能获取一个到 ByteBufAllocator 的引用。对于这种情况，Netty 提供了一个简单的称为 Unpooled 的工具类，它提供了静态的辅助方法来创建未池化的 ByteBuf实例。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216152024453.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

Unpooled 类还使得 ByteBuf 同样可用于那些并不需要 Netty 的其他组件的非网络项目，使得其能得益于高性能的可扩展的缓冲区 API。



**5.5.3 ByteBufUtil 类**

ByteBufUtil 提供了用于操作 ByteBuf 的静态的辅助方法。因为这个 API 是通用的，并且和池化无关，所以这些方法已然在分配类的外部实现。

这些静态方法中最有价值的可能就是 hexdump()方法，它以十六进制的表示形式打印ByteBuf 的内容。这在各种情况下都很有用，例如，出于调试的目的记录 ByteBuf 的内容。十六进制的表示通常会提供一个比字节值的直接表示形式更加有用的日志条目，此外，十六进制的版本还可以很容易地转换回实际的字节表示。

另一个有用的方法是 boolean equals(ByteBuf, ByteBuf)，它被用来判断两个ByteBuf实例的相等性。如果你实现自己的 ByteBuf 子类，你可能会发现 ByteBufUtil 的其他有用方法

### 5.6 引用计数

引用计数是一种通过在某个对象所持有的资源不再被其他对象引用时释放该对象所持有的资源来优化内存使用和性能的技术。Netty 在第 4 版中为 ByteBuf 和 ByteBufHolder 引入了引用计数技术，它们都实现了 interface ReferenceCounted。

引用计数背后的想法并不是特别的复杂；它主要涉及跟踪到某个特定对象的活动引用的数量。一个 ReferenceCounted 实现的实例将通常以活动的引用计数为 1 作为开始。只要引用计数大于 0，就能保证对象不会被释放。当活动引用的数量减少到 0 时，该实例就会被释放。

注意，虽然释放的确切语义可能是特定于实现的，但是至少已经释放的对象应该不可再用了。引用计数对于池化实现（如 PooledByteBufAllocator）来说是至关重要的，它降低了内存分配的开销。代码清单 5-15 和代码清单 5-16 展示了相关的示例。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201216152315679.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

试图访问一个已经被释放的引用计数的对象，将会导致一个 IllegalReferenceCountException。

注意，一个特定的（ReferenceCounted 的实现）类，可以用它自己的独特方式来定义它的引用计数规则。例如，我们可以设想一个类，其 release()方法的实现总是将引用计数设为零，而不用关心它的当前值，从而一次性地使所有的活动引用都失效。

>谁负责释放 一般来说，是由最后访问（引用计数）对象的那一方来负责将它释放。在第 6 章中，我们将会解释这个概念和 ChannelHandler 以及 ChannelPipeline 的相关性。