##  第  6章 ChannelHandler和ChannelPipeline

### 6.1 ChannelHandler家族

**6.1.1 Channel 的生命周期**

Interface Channel 定义了一组和 ChannelInboundHandler API 密切相关的简单但功能强大的状态模型，Channel 的这4 个状态。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217151713782.png)

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217151723231.png)

Channel 的正常生命周期如图 6-1 所示。当这些状态发生改变时，将会生成对应的事件。这些事件将会被转发给 ChannelPipeline 中的 ChannelHandler，其可以随后对它们做出响应。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217151814924.png)

**6.1.2 ChannelHandler 的生命周期**

表 6-2 中列出了 interface ChannelHandler 定义的生命周期操作，在 ChannelHandler被添加到 ChannelPipeline 中或者被从 ChannelPipeline 中移除时会调用这些操作。这些方法中的每一个都接受一个 ChannelHandlerContext 参数

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217151916342.png)

Netty 定义了下面两个重要的 ChannelHandler 子接口： 

 ChannelInboundHandler——处理入站数据以及各种状态变化； 

 ChannelOutboundHandler——处理出站数据并且允许拦截所有的操作。

在接下来的章节中，我们将详细地讨论这些子接口。

**6.1.3 ChannelInboundHandler 接口**

表 6-3 列出了 interface ChannelInboundHandler 的生命周期方法。这些方法将会在数据被接收时或者与其对应的 Channel 状态发生改变时被调用。正如我们前面所提到的，这些方法和 Channel 的生命周期密切相关。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217152215910.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

当某个 ChannelInboundHandler 的实现重写 channelRead()方法时，它将负责显式地释放与池化的 ByteBuf 实例相关的内存。Netty 为此提供了一个实用方法 ReferenceCountUtil.release()，如代码清单 6-1 所示。

代码清单 6-1 释放消息资源

```java
@Sharable
public class DiscardHandler extends ChannelInboundHandlerAdapter {
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		ReferenceCountUtil.release(msg);//丢弃已接收的消息
	} 
}
```

Netty 将使用 WARN 级别的日志消息记录未释放的资源，使得可以非常简单地在代码中发现违规的实例。但是以这种方式管理资源可能很繁琐。一个更加简单的方式是使用 SimpleChannelInboundHandler。代码清单 6-2 是代码清单 6-1 的一个变体，说明了这一点。

代码清单 6-2 使用 **SimpleChannelInboundHandler**

```java
public class SimpleDiscardHandler extends SimpleChannelInboundHandler<Object> {
	@Override
	public void channelRead0(ChannelHandlerContext ctx, Object msg) {
		// No need to do anything special 不需要任何显式的资源释放
	} 
}
```

由于 SimpleChannelInboundHandler 会自动释放资源，所以你不应该存储指向任何消息的引用供将来使用，因为这些引用都将会失效。

**6.1.4 ChannelOutboundHandler 接口**

出站操作和数据将由 ChannelOutboundHandler 处理。它的方法将被 Channel、ChannelPipeline 以及ChannelHandlerContext 调用。

ChannelOutboundHandler 的一个强大的功能是可以按需推迟操作或者事件，这使得可以通过一些复杂的方法来处理请求。例如，如果到远程节点的写入被暂停了，那么你可以推迟冲 刷操作并在稍后继续。

表6-4显示了所有由ChannelOutboundHandler本身所定义的方法（忽略了那些从ChannelHandler 继承的方法）

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217152813288.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

**6.1.5 ChannelHandler 适配器**

你可以使用 ChannelInboundHandlerAdapter 和 ChannelOutboundHandlerAdapter类作为自己的 ChannelHandler 的起始点。这两个适配器分别提供了 ChannelInboundHandler和 ChannelOutboundHandler 的基本实现。通过扩展抽象类 ChannelHandlerAdapter，它们获得了它们共同的超接口 ChannelHandler 的方法。生成的类的层次结构如图 6-2 所示。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217153007864.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)



ChannelHandlerAdapter 还提供了实用方法 isSharable()。如果其对应的实现被标注为 Sharable，那么这个方法将返回 true，表示它可以被添加到多个 ChannelPipeline中（如在 2.3.1 节中所讨论过的一样）。 

在 ChannelInboundHandlerAdapter 和 ChannelOutboundHandlerAdapter 中所提供的方法体调用了其相关联的 ChannelHandlerContext 上的等效方法，从而将事件转发到了 ChannelPipeline 中的下一个 ChannelHandler 中。

你要想在自己的 ChannelHandler 中使用这些适配器类，只需要简单地扩展它们，并且重写那些你想要自定义的方法。

**6.1.6 资源管理**

每当通过调用 ChannelInboundHandler.channelRead()或者 ChannelOutboundHandler.write()方法来处理数据时，你都需要确保没有任何的资源泄漏。你可能还记得在前面的章节中所提到的，Netty 使用引用计数来处理池化的 ByteBuf。所以在完全使用完某个ByteBuf 后，调整其引用计数是很重要的。

为了帮助你诊断潜在的（资源泄漏）问题，Netty提供了class ResourceLeakDetector

它将对你应用程序的缓冲区分配做大约 1%的采样来检测内存泄露。相关的开销是非常小的。

Netty 目前定义了 4 种泄漏检测级别，如表 6-5 所示：

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217153644182.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

泄露检测级别可以通过将下面的 Java 系统属性设置为表中的一个值来定义

java -Dio.netty.leakDetectionLevel=ADVANCED

如果带着该 JVM 选项重新启动你的应用程序，你将看到自己的应用程序最近被泄漏的缓冲

区被访问的位置。下面是一个典型的由单元测试产生的泄漏报告：

```java
Running io.netty.handler.codec.xml.XmlFrameDecoderTest
15:03:36.886 [main] ERROR io.netty.util.ResourceLeakDetector - LEAK:
ByteBuf.release() was not called before it's garbage-collected.
Recent access records: 1
#1: io.netty.buffer.AdvancedLeakAwareByteBuf.toString(
AdvancedLeakAwareByteBuf.java:697)
io.netty.handler.codec.xml.XmlFrameDecoderTest.testDecodeWithXml(
XmlFrameDecoderTest.java:157)
io.netty.handler.codec.xml.XmlFrameDecoderTest.testDecodeWithTwoMessages(
XmlFrameDecoderTest.java:133)
...
```

实现 ChannelInboundHandler.channelRead()和 ChannelOutboundHandler.write()方法时，应该如何使用这个诊断工具来防止泄露呢？让我们看看你的 channelRead()操作直接消费入站消息的情况；也就是说，它不会通过调用 ChannelHandlerContext.fireChannelRead()方法将入站消息转发给下一个 ChannelInboundHandler。代码清单 6-3 展示了如何释放消息。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217153851935.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

在出站方向这边，如果你处理了 write()操作并丢弃了一个消息，那么你也应该负责释放它。代码清单 6-4 展示了一个丢弃所有的写入数据的实现。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217153918831.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)



重要的是，不仅要释放资源，还要通知 ChannelPromise。否则可能会出现 ChannelFutureListener 收不到某个消息已经被处理了的通知的情况。

总之，如果一个消息被消费或者丢弃了，并且没有传递给 ChannelPipeline 中的下一个ChannelOutboundHandler，那么用户就有责任调用 ReferenceCountUtil.release()。如果消息到达了实际的传输层，那么当它被写入时或者 Channel 关闭时，都将被自动释放。



### 6.2 ChannelPipeline 接口

如果你认为ChannelPipeline是一个拦截流经Channel的入站和出站事件的ChannelHandler 实例链，那么就很容易看出这些 ChannelHandler 之间的交互是如何组成一个应用程序数据和事件处理逻辑的核心的。

每一个新创建的 Channel 都将会被分配一个新的 ChannelPipeline。这项关联是永久性的；Channel 既不能附加另外一个 ChannelPipeline，也不能分离其当前的。在 Netty 组件的生命周期中，这是一项固定的操作，不需要开发人员的任何干预。

根据事件的起源，事件将会被 ChannelInboundHandler 或者 ChannelOutboundHandler处理。随后，通过调用 ChannelHandlerContext 实现，它将被转发给同一超类型的下一个ChannelHandler

图 6-3 展示了一个典型的同时具有入站和出站 ChannelHandler 的 ChannelPipeline 的布局，并且印证了我们之前的关于 ChannelPipeline 主要由一系列的 ChannelHandler 所组成的说法。ChannelPipeline 还提供了通过 ChannelPipeline 本身传播事件的方法。如果一个入站事件被触发，它将被从 ChannelPipeline 的头部开始一直被传播到 Channel Pipeline 的尾端。在图 6-3 中，一个出站 I/O 事件将从 ChannelPipeline 的最右边开始，然后向左传播。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217154540631.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

在 ChannelPipeline 传播事件时，它会测试 ChannelPipeline 中的下一个 ChannelHandler 的类型是否和事件的运动方向相匹配。如果不匹配，ChannelPipeline 将跳过该

ChannelHandler 并前进到下一个，直到它找到和该事件所期望的方向相匹配的为止。（当然，ChannelHandler 也可以同时实现 ChannelInboundHandler 接口和 ChannelOutboundHandler 接口。）



**6.2.1 修改 ChannelPipeline**

ChannelHandler 可以通过添加、删除或者替换其他的 ChannelHandler 来实时地修改ChannelPipeline 的布局。（它也可以将它自己从 ChannelPipeline 中移除。）这是 ChannelHandler 最重要的能力之一，所以我们将仔细地来看看它是如何做到的。表 6-6 列出了相关的方法。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217155411132.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217155802311.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

稍后，你将看到，重组 ChannelHandler 的这种能力使我们可以用它来轻松地实现极其灵活的逻辑。

还有别的通过类型或者名称来访问 ChannelHandler 的方法。这些方法都列在了表 6-7 中

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217155850739.png)

**6.2.2 触发事件**

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217160018382.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)



![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217160038858.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

总结一下：

 ChannelPipeline 保存了与 Channel 相关联的 ChannelHandler； 

 ChannelPipeline 可以根据需要，通过添加或者删除 ChannelHandler 来动态地修改； 

 ChannelPipeline 有着丰富的 API 用以被调用，以响应入站和出站事件。



### 6.3 ChannelHandlerContext 接口

ChannelHandlerContext 代表了 ChannelHandler 和 ChannelPipeline 之间的关联，每当有 ChannelHandler 添加到 ChannelPipeline 中时，都会创建 ChannelHandlerContext。ChannelHandlerContext 的主要功能是管理它所关联的 ChannelHandler 和在同一个 ChannelPipeline 中的其他 ChannelHandler 之间的交互。

ChannelHandlerContext 有很多的方法，其中一些方法也存在于 Channel 和 ChannelPipeline 本身上，但是有一点重要的不同。如果调用 Channel 或者 ChannelPipeline 上的这些方法，它们将沿着整个 ChannelPipeline 进行传播。而调用位于 ChannelHandlerContext上的相同方法，则将从当前所关联的 ChannelHandler 开始，并且只会传播给位于该ChannelPipeline 中的下一个能够处理该事件的 ChannelHandler。

当使用 ChannelHandlerContext 的 API 的时候，请牢记以下两点： 

 ChannelHandlerContext 和 ChannelHandler 之间的关联（绑定）是永远不会改变的，所以缓存对它的引用是安全的； 

 如同我们在本节开头所解释的一样，相对于其他类的同名方法，ChannelHandlerContext的方法将产生更短的事件流，应该尽可能地利用这个特性来获得最大的性能

**6.3.1 使用 ChannelHandlerContext**

在这一节中我们将讨论 ChannelHandlerContext 的用法，以及存在于 ChannelHandlerContext、Channel 和 ChannelPipeline 上的方法的行为。图 6-4 展示了它们之间的关系。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217160903961.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

在代码清单 6-6 中，将通过 ChannelHandlerContext 获取到 Channel 的引用。调用Channel上的 write()方法将会导致写入事件从尾端到头部地流经 ChannelPipeline。

```java
ChannelHandlerContext ctx = ..;
Channel channel = ctx.channel();
channel.write(Unpooled.copiedBuffer("Netty in Action",CharsetUtil.UTF_8));
```



代码清单 6-7 展示了一个类似的例子，但是这一次是写入 ChannelPipeline。我们再次看到，（到 ChannelPipline 的）引用是通过 ChannelHandlerContext 获取的。

```java
ChannelHandlerContext ctx = ..;
ChannelPipeline pipeline = ctx.pipeline();
pipeline.write(Unpooled.copiedBuffer("Netty in Action",CharsetUtil.UTF_8));
```

如同在图 6-5 中所能够看到的一样，代码清单 6-6 和代码清单 6-7 中的事件流是一样的。重要的是要注意到，虽然被调用的 Channel 或 ChannelPipeline 上的 write()方法将一直传播事件通过整个 ChannelPipeline，但是在 ChannelHandler 的级别上，事件从一个 ChannelHandler到下一个 ChannelHandler 的移动是由 ChannelHandlerContext 上的调用完成的。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201217161500147.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

为什么会想要从 ChannelPipeline 中的某个特定点开始传播事件呢？ 

 为了减少将事件传经对它不感兴趣的 ChannelHandler 所带来的开销。

 为了避免将事件传经那些可能会对它感兴趣的 ChannelHandler。

要想调用从某个特定的 ChannelHandler 开始的处理过程，必须获取到在（ChannelPipeline）该 ChannelHandler 之前的 ChannelHandler 所关联的 ChannelHandlerContext。这个 ChannelHandlerContext 将调用和它所关联的 ChannelHandler 之后的ChannelHandler。

代码清单 6-8 和图 6-6 说明了这种用法。

```java
ChannelHandlerContext ctx = ..;
ctx.write(Unpooled.copiedBuffer("Netty in Action", CharsetUtil.UTF_8));
```

如图 6-6 所示，消息将从下一个 ChannelHandler 开始流经 ChannelPipeline，绕过了所有前面的 ChannelHandler。

![image-20201217161859589](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20201217161859589.png)

**6.3.2 ChannelHandler 和 ChannelHandlerContext 的高级用法**

正如我们在代码清单 6-6 中所看到的，你可以通过调用 ChannelHandlerContext 上的pipeline()方法来获得被封闭的 ChannelPipeline 的引用。这使得运行时得以操作ChannelPipeline 的 ChannelHandler，我们可以利用这一点来实现一些复杂的设计。例如，你可以通过将 ChannelHandler 添加到 ChannelPipeline 中来实现动态的协议切换。

另一种高级的用法是缓存到 ChannelHandlerContext 的引用以供稍后使用，这可能会发生在任何的 ChannelHandler 方法之外，甚至来自于不同的线程。代码清单 6-9 展示了用这种模式来触发事件.

```java
public class WriteHandler extends ChannelHandlerAdapter { 
	 private ChannelHandlerContext ctx;
	 @Override
	 public void handlerAdded(ChannelHandlerContext ctx) { 
 		this.ctx = ctx; 
	 } 
     public void send(String msg) { 
		ctx.writeAndFlush(msg);
 	 } 
}
```

因为一个 ChannelHandler 可以从属于多个 ChannelPipeline，所以它也可以绑定到多个 ChannelHandlerContext 实例。对于这种用法指在多个 ChannelPipeline 中共享同一 个 ChannelHandler，对应的 ChannelHandler 必须要使用@Sharable 注解标注；否则，试图将它添加到多个 ChannelPipeline 时将会触发异常。显而易见，为了安全地被用于多个并发的 Channel（即连接），这样的 ChannelHandler 必须是线程安全的。

```java
@Sharable 
public class SharableHandler extends ChannelInboundHandlerAdapter { 
 	@Override
 	public void channelRead(ChannelHandlerContext ctx, Object msg) { 
 		System.out.println("Channel read message: " + msg);
 	ctx.fireChannelRead(msg); 
 } 
}
```

前面的 ChannelHandler 实现符合所有的将其加入到多个 ChannelPipeline 的需求，即它使用了注解@Sharable 标注，并且也不持有任何的状态。

总之，只应该在确定了你的 ChannelHandler 是线程安全的时才使用@Sharable 注解。

### 6.4 异常处理

**6.4.1 处理入站异常**

如果在处理入站事件的过程中有异常被抛出，那么它将从它在 ChannelInboundHandler里被触发的那一点开始流经 ChannelPipeline。要想处理这种类型的入站异常，你需要在你的 ChannelInboundHandler 实现中重写下面的方法。

```java
public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
```

因为异常将会继续按照入站方向流动（就像所有的入站事件一样），所以实现了前面所示逻辑的 ChannelInboundHandler 通常位于 ChannelPipeline 的最后。这确保了所有的入站异常都总是会被处理，无论它们可能会发生在 ChannelPipeline 中的什么位置。

你应该如何响应异常，可能很大程度上取决于你的应用程序。你可能想要关闭Channel（和连接），也可 能会尝试进行恢复。如果你不实现任何处理入站异常的逻辑（或者没有消费该异常），那么Netty将会记录该异常没有被处理的事实

总结一下：

 ChannelHandler.exceptionCaught()的默认实现是简单地将当前异常转发给ChannelPipeline 中的下一个 ChannelHandler； 

 如果异常到达了 ChannelPipeline 的尾端，它将会被记录为未被处理； 

 要想定义自定义的处理逻辑，你需要重写 exceptionCaught()方法。然后你需要决定是否需要将该异常传播出去。



**6.4.2 处理出站异常**

用于处理出站操作中的正常完成以及异常的选项，都基于以下的通知机制。 

 每个出站操作都将返回一个 ChannelFuture。注册到 ChannelFuture 的 ChannelFutureListener 将在操作完成时被通知该操作是成功了还是出错了。 

 几乎所有的 ChannelOutboundHandler 上的方法都会传入一个 ChannelPromise的实例。作为 ChannelFuture 的子类，ChannelPromise 也可以被分配用于异步通知的监听器。但是，ChannelPromise 还具有提供立即通知的可写方法：

```java
ChannelPromise setSuccess();

ChannelPromise setFailure(Throwable cause);

```

添加 ChannelFutureListener 只需要调用 ChannelFuture 实例上的 addListener(ChannelFutureListener)方法，并且有两种不同的方式可以做到这一点。其中最常用的方式是，调用出站操作（如 write()方法）所返回的 ChannelFuture 上的 addListener()方法。

代码清单 6-13 使用了这种方式来添加 ChannelFutureListener，它将打印栈跟踪信息并且随后关闭 Channel。

```java
ChannelFuture future = channel.write(someMessage);
future.addListener(new ChannelFutureListener() {
	@Override
	public void operationComplete(ChannelFuture f) {
		if (!f.isSuccess()) {
			f.cause().printStackTrace();
			f.channel().close();
		} 
    }
});
```

第二种方式是将 ChannelFutureListener 添加到即将作为参数传递给 ChannelOutboundHandler 的方法的 ChannelPromise。代码清单 6-14 中所展示的代码和代码清单 6-13中所展示的具有相同的效果。

```java
public class OutboundExceptionHandler extends ChannelOutboundHandlerAdapter {
	@Override
	public void write(ChannelHandlerContext ctx, Object msg,ChannelPromise promise) {
		promise.addListener(new ChannelFutureListener() {
		@Override
		public void operationComplete(ChannelFuture f) {
			if (!f.isSuccess()) {
				f.cause().printStackTrace();
				f.channel().close();
			}
        }
		});
	} 
}	
```

为何选择一种方式而不是另一种呢？对于细致的异常处理，你可能会发现，在调用出站操作时添加 ChannelFutureListener 更合适，如代码清单 6-13 所示。而对于一般的异常处理，你可能会发现，代码清单 6-14 所示的自定义的 ChannelOutboundHandler 实现的方式更加的简单。

如果你的 ChannelOutboundHandler 本身抛出了异常会发生什么呢？在这种情况下，Netty 本身会通知任何已经注册到对应 ChannelPromise 的监听器。

