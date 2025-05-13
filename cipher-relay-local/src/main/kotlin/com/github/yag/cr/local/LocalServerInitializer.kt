/*
 * Copyright 2024-2025 marks.yag@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.yag.cr.local

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import ketty.core.client.KettyClient

class LocalServerInitializer(key: String, client: KettyClient, connectionManager: ConnectionManager, metrics: Metrics) : ChannelInitializer<SocketChannel>() {

    private val mixInServerHandler = SelectHandler(key, client, connectionManager, metrics)

    @Throws(Exception::class)
    public override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(
            LoggingHandler(LogLevel.DEBUG),
            mixInServerHandler
        )
    }
}