## 第2章 构建一个基于 Netty 的客户端和服务器

### 一、应用架构

 这是从高层次上展示了一个我将要编写的客户端和服务器应用程序。 

![](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/2c8bfc6e4cd542b9bf835c75f405f19e~tplv-k3u1fbpfcp-watermark.image)



客户端和服务器的交互非常简单，在客户端建立一个连接之后，它会向服务器发送一个或多个消息，反过来，服务器又会将每个消息回送给客户端。虽然它本身看起来好像用处不大，但它充分地体现了客户端/服务器系统中典型的请求-响应交互模式。



### 二、编写

所有的 Netty 服务器都需要以下两部分。 

- 至少一个 ChannelHandler—该组件实现了服务器对从客户端接收的数据的处理，即它的业务逻辑。
- ServerBootstrap这是配置服务器的启动代码。至少，它会将服务器绑定到它要监听连接请求的端口上。

#### 1、ChannelHandler 和业务逻辑 

因为服务器会响应传入的消息，所以它需要实现 ChannelInboundHandler 接口，用来定义响应入站事件的方法。这个简单的应用程序只需要用到少量的这些方法，所以继承 ChannelInboundHandlerAdapter 类也就足够了，它提供了ChannelInboundHandler 的默认实现。

- channelRead()—对于每个传入的消息都要调用 
- channelReadComplete()—通知ChannelInboundHandler最后一次对 channelRead的调用是当前批量读取中的最后一条消息；
- exceptionCaught()—在读取操作期间，有异常抛出时会调用。



ChannelInboundHandlerAdapter 有一个直观的 API，并且它的每个方法都可以被重写以挂钩到事件生命周期的恰当点上。因为需要处理所有接收到的数据，所以你重写了channelRead()方法。在这个服务器应用程序中，你将数据简单地回送给了远程节点。

重写 exceptionCaught()方法允许你对 Throwable 的任何子类型做出反应，在这里你记录了异常并关闭了连接。虽然一个更加完善的应用程序也许会尝试从异常中恢复，但在这个场景下，只是通过简单地关闭连接来通知远程节点发生了错误。

> 如果不捕获异常，会发生什么呢 
>
> 每个 Channel 都拥有一个与之相关联的 ChannelPipeline，其持有一个 ChannelHandler 的 
>
> 实例链。在默认的情况下，ChannelHandler 会把对它的方法的调用转发给链中的下一个 Channel
>
> Handler。因此，如果 exceptionCaught()方法没有被该链中的某处实现，那么所接收的异常将会被 
>
> 传递到 ChannelPipeline 的尾端并被记录。为此，你的应用程序应该提供至少有一个实现了 
>
> exceptionCaught()方法的 ChannelHandler。（6.4 节详细地讨论了异常处理）。

请记住下面这些关键点： 

- 针对不同类型的事件来调用 ChannelHandler；
- 应用程序通过实现或者扩展 ChannelHandler 来挂钩到事件的生命周期，并且提供自定义的应用程序逻辑；
- 在架构上，ChannelHandler 有助于保持业务逻辑与网络处理代码的分离。这简化了开发过程，因为代码必须不断地演化以响应不断变化的需求


2、ServerBootstrap

- EchoServerHandler 实现了业务逻辑
- main()方法引导了服务器

引导过程中：

- 创建一个 ServerBootstrap 的实例以引导和绑定服务器 ��d ded�
- 创建并分配一个 NioEventLoopGroup 实例以进行事件的处理，如接受新连接以及读/写数据
- 指定服务器绑定的本地的 InetSocketAddress 
- 使用一个 EchoServerHandler 的实例初始化每一个新的 Channel
- 调用 ServerBootstrap.bind()方法以绑定服务器。 



3、 通过 ChannelHandler 实现客户端逻辑

扩展 SimpleChannelInboundHandler 类以处理所有必须的任务 ，重写下面的方法：

- channelActive()——在到服务器的连接已经建立之后将被调用； 
- channelRead0() ——当从服务器接收到一条消息时被调用； 
- exceptionCaught()——在处理过程中引发异常时被调用 



为什么我们在客户端使用的是 SimpleChannelInboundHandler，而不是在 Echo

ServerHandler 中所使用的 ChannelInboundHandlerAdapter 呢？这和两个因素的相互作用有 

关：业务逻辑如何处理消息以及 Netty 如何管理资源。

在客户端，当 channelRead0()方法完成时，你已经有了传入消息，并且已经处理完它了。当该方 

法返回时，SimpleChannelInboundHandler 负责释放指向保存该消息的 ByteBuf 的内存引用。

在 EchoServerHandler 中，你仍然需要将传入消息回送给发送者，而 write()操作是异步的，直 

到 channelRead()方法返回后可能仍然没有完成（如代码清单 2-1 所示）。为此，EchoServerHandler 

扩展了 ChannelInboundHandlerAdapter，其在这个时间点上不会释放消息

消息在 EchoServerHandler 的 channelReadComplete()方法中，当 writeAndFlush()方 

法被调用时被释放（见代码清单 2-1）。





4、客户端编写

- 为初始化客户端，创建了一个 Bootstrap 实例 
- 为进行事件处理分配了一个 NioEventLoopGroup 实例，其中事件处理包括创建新的连接以及处理入站和出站数据； 
- 为服务器连接创建了一个 InetSocketAddress 实例
- 当连接被建立时，一个 EchoClientHandler 实例会被安装到（该 Channel 的） ChannelPipeline 中
- 在一切都设置完成后，调用 Bootstrap.connect()方法连接到远程节点 



